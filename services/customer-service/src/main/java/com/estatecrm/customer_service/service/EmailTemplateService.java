package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.dto.CreateEmailTemplateRequest;
import com.estatecrm.customer_service.dto.EmailTemplateResponse;
import com.estatecrm.customer_service.dto.UpdateEmailTemplateRequest;
import com.estatecrm.customer_service.entity.EmailTemplate;
import com.estatecrm.customer_service.enums.EmailTemplateCategory;
import com.estatecrm.customer_service.enums.EmailTemplateStatus;
import com.estatecrm.customer_service.exception.ConflictException;
import com.estatecrm.customer_service.repository.EmailTemplateRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Kho mau email. Khac voi khach hang/nhat ky, day la du lieu dung chung nen
 * ADMIN/MANAGER moi duoc tao/sua/xoa; SALES chi duoc doc de chon mau gui.
 */
@Service
public class EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;

    public EmailTemplateService(EmailTemplateRepository emailTemplateRepository) {
        this.emailTemplateRepository = emailTemplateRepository;
    }

    /** Tao mau moi. Ten khong duoc trung, khop unique index uq_email_templates_name. */
    @Transactional
    public EmailTemplateResponse create(
            CreateEmailTemplateRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        if (emailTemplateRepository.existsByName(request.name())) {
            throw new ConflictException("Template name already exists");
        }
        EmailTemplate template = new EmailTemplate();
        template.setName(request.name());
        template.setSubject(request.subject());
        template.setBody(request.body());
        template.setCategory(request.category());
        template.setStatus(request.status() == null ? EmailTemplateStatus.ACTIVE : request.status());
        template.setCreatedBy(actorId);
        return EmailTemplateResponse.from(emailTemplateRepository.save(template));
    }

    /** Danh sach mau, loc theo category/status/keyword (ten hoac subject). */
    @Transactional(readOnly = true)
    public Page<EmailTemplateResponse> list(
            EmailTemplateCategory category,
            EmailTemplateStatus status,
            String keyword,
            Pageable pageable) {
        return emailTemplateRepository
                .findAll(EmailTemplateRepository.filter(category, status, keyword), pageable)
                .map(EmailTemplateResponse::from);
    }

    @Transactional(readOnly = true)
    public EmailTemplateResponse get(UUID templateId) {
        return EmailTemplateResponse.from(requireTemplate(templateId));
    }

    /** Sua mot phan mau. Field null = giu nguyen gia tri cu. */
    @Transactional
    public EmailTemplateResponse update(
            UUID templateId, UpdateEmailTemplateRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        EmailTemplate template = requireTemplate(templateId);

        if (request.name() != null && !request.name().equals(template.getName())) {
            if (emailTemplateRepository.existsByName(request.name())) {
                throw new ConflictException("Template name already exists");
            }
            template.setName(request.name());
        }
        if (request.subject() != null) {
            template.setSubject(request.subject());
        }
        if (request.body() != null) {
            template.setBody(request.body());
        }
        if (request.category() != null) {
            template.setCategory(request.category());
        }
        if (request.status() != null) {
            template.setStatus(request.status());
        }
        template.setUpdatedBy(actorId);
        return EmailTemplateResponse.from(emailTemplateRepository.save(template));
    }

    @Transactional
    public void delete(UUID templateId, UUID actorId, String role) {
        requirePrivileged(role);
        emailTemplateRepository.delete(requireTemplate(templateId));
    }

    private EmailTemplate requireTemplate(UUID templateId) {
        return emailTemplateRepository
                .findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Email template not found"));
    }

    /** Chi ADMIN/MANAGER duoc thay doi kho mau dung chung. */
    private void requirePrivileged(String role) {
        if (!"ADMIN".equals(role) && !"MANAGER".equals(role)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only ADMIN or MANAGER can manage email templates");
        }
    }
}
