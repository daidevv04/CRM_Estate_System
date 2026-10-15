package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.dto.CreateEmailCareRequest;
import com.estatecrm.customer_service.dto.EmailCareResponse;
import com.estatecrm.customer_service.dto.SendCustomerEmailRequest;
import com.estatecrm.customer_service.dto.UpdateEmailCareRequest;
import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.entity.CustomerCare;
import com.estatecrm.customer_service.entity.EmailCare;
import com.estatecrm.customer_service.entity.EmailTemplate;
import com.estatecrm.customer_service.enums.CareType;
import com.estatecrm.customer_service.enums.EmailCareStatus;
import com.estatecrm.customer_service.repository.CustomerCareRepository;
import com.estatecrm.customer_service.repository.EmailCareRepository;
import com.estatecrm.customer_service.repository.EmailTemplateRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class EmailCareService {

    private static final Logger log = LoggerFactory.getLogger(EmailCareService.class);

    private final EmailCareRepository emailCareRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final CustomerCareRepository customerCareRepository;
    private final CustomerService customerService;
    private final MailClient mailClient;

    public EmailCareService(
            EmailCareRepository emailCareRepository,
            EmailTemplateRepository emailTemplateRepository,
            CustomerCareRepository customerCareRepository,
            CustomerService customerService,
            MailClient mailClient) {
        this.emailCareRepository = emailCareRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.customerCareRepository = customerCareRepository;
        this.customerService = customerService;
        this.mailClient = mailClient;
    }

    @Transactional
    public EmailCareResponse create(
            UUID customerId, UUID careId, CreateEmailCareRequest request, UUID actorId, String role) {
        CustomerCare care = requireCare(customerId, careId, actorId, role);

        EmailTemplate template = null;
        if (request.templateId() != null) {
            template = emailTemplateRepository.findById(request.templateId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Email template not found"));
        }

        String subject = request.subject() != null ? request.subject()
                : template == null ? null : template.getSubject();
        String body = request.body() != null ? request.body()
                : template == null ? null : template.getBody();
        if (subject == null || body == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "subject and body are required when no template is given");
        }

        EmailCare careMail = new EmailCare();
        careMail.setCustomerCare(care);
        careMail.setTemplate(template);
        careMail.setToEmail(request.toEmail());
        careMail.setSubject(subject);
        careMail.setBody(body);
        careMail.setStatus(request.status() == null ? EmailCareStatus.SENT : request.status());

        if (careMail.getStatus() == EmailCareStatus.SENT) {

            mailClient.send(request.toEmail(), subject, body);
            careMail.setSentAt(LocalDateTime.now());
        }
        careMail.setCreatedBy(actorId);
        return EmailCareResponse.from(emailCareRepository.save(careMail));
    }

    @Transactional
    public EmailCareResponse sendToCustomer(
            UUID customerId, SendCustomerEmailRequest request, UUID actorId, String role) {
        if (!"MANAGER".equals(role) && !"SALES".equals(role)) {
            throw new ResponseStatusException(
                    FORBIDDEN, "Chỉ Manager hoặc Sales mới có quyền gửi email chăm sóc khách hàng");
        }

        Customer customer = customerService.requireAccess(customerId, actorId, role);
        String toEmail = request.toEmail() != null && !request.toEmail().isBlank()
                ? request.toEmail().trim()
                : customer.getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Khách hàng chưa có địa chỉ email để gửi");
        }

        EmailTemplate template = null;
        if (request.templateId() != null) {
            template = emailTemplateRepository.findById(request.templateId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Email template not found"));
        }

        String subject = request.subject() != null && !request.subject().isBlank()
                ? request.subject()
                : template == null ? null : template.getSubject();
        String body = request.body() != null && !request.body().isBlank()
                ? request.body()
                : template == null ? null : template.getBody();
        if (subject == null || body == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Tiêu đề và nội dung email không được để trống");
        }

        CustomerCare care = new CustomerCare();
        care.setCustomer(customer);
        care.setType(CareType.EMAIL);
        String careContent = (request.note() != null && !request.note().isBlank())
                ? request.note()
                : "[Gửi email chăm sóc] Tiêu đề: " + subject;
        care.setContent(careContent);
        care.setCreatedBy(actorId);
        care = customerCareRepository.save(care);

        EmailCare careMail = new EmailCare();
        careMail.setCustomerCare(care);
        careMail.setTemplate(template);
        careMail.setToEmail(toEmail);
        careMail.setSubject(subject);
        careMail.setBody(body);
        careMail.setStatus(EmailCareStatus.SENT);
        careMail.setCreatedBy(actorId);

        try {
            mailClient.send(toEmail, subject, body, body, request.senderEmail(), request.senderName());
            careMail.setSentAt(LocalDateTime.now());
            careMail.setStatus(EmailCareStatus.SENT);
            return EmailCareResponse.from(emailCareRepository.save(careMail));
        } catch (Exception e) {
            log.error("Lỗi gửi email chăm sóc tới {}: {}", toEmail, e.getMessage(), e);
            careMail.setStatus(EmailCareStatus.FAILED);
            emailCareRepository.save(careMail);
            throw new ResponseStatusException(
                    BAD_GATEWAY, "Gửi email tới " + toEmail + " thất bại. " + (e.getMessage() != null ? e.getMessage() : "Vui lòng kiểm tra dịch vụ mail."));
        }
    }

    @Transactional(readOnly = true)
    public Page<EmailCareResponse> listByCare(
            UUID customerId,
            UUID careId,
            EmailCareStatus status,
            UUID actorId,
            String role,
            Pageable pageable) {
        requireCare(customerId, careId, actorId, role);
        return emailCareRepository
                .findAll(EmailCareRepository.filter(careId, status), pageable)
                .map(EmailCareResponse::from);
    }

    @Transactional(readOnly = true)
    public EmailCareResponse get(UUID customerId, UUID emailId, UUID actorId, String role) {
        return EmailCareResponse.from(requireEmail(customerId, emailId, actorId, role));
    }

    @Transactional
    public EmailCareResponse update(
            UUID customerId, UUID emailId, UpdateEmailCareRequest request, UUID actorId, String role) {
        EmailCare careMail = requireEmail(customerId, emailId, actorId, role);
        if (request.status() != null) {
            careMail.setStatus(request.status());
            if (request.status() == EmailCareStatus.OPENED && careMail.getOpenedAt() == null) {
                careMail.setOpenedAt(LocalDateTime.now());
            }
            if (request.status() == EmailCareStatus.SENT && careMail.getSentAt() == null) {

                mailClient.send(careMail.getToEmail(), careMail.getSubject(), careMail.getBody());
                careMail.setSentAt(LocalDateTime.now());
            }
        }
        if (request.sentAt() != null) {
            careMail.setSentAt(request.sentAt());
        }
        careMail.setUpdatedBy(actorId);
        return EmailCareResponse.from(emailCareRepository.save(careMail));
    }

    @Transactional
    public void delete(UUID customerId, UUID emailId, UUID actorId, String role) {
        emailCareRepository.delete(requireEmail(customerId, emailId, actorId, role));
    }

    private CustomerCare requireCare(UUID customerId, UUID careId, UUID actorId, String role) {
        customerService.requireAccess(customerId, actorId, role);
        return customerCareRepository.findById(careId)
                .filter(care -> care.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Customer care not found"));
    }

    private EmailCare requireEmail(UUID customerId, UUID emailId, UUID actorId, String role) {
        EmailCare careMail = emailCareRepository.findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Email care not found"));
        requireCare(customerId, careMail.getCustomerCare().getId(), actorId, role);
        return careMail;
    }
}
