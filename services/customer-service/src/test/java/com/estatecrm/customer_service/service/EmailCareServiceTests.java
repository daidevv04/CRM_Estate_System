package com.estatecrm.customer_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.estatecrm.customer_service.dto.CreateEmailCareRequest;
import com.estatecrm.customer_service.dto.UpdateEmailCareRequest;
import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.entity.CustomerCare;
import com.estatecrm.customer_service.entity.EmailCare;
import com.estatecrm.customer_service.entity.EmailTemplate;
import com.estatecrm.customer_service.enums.EmailCareStatus;
import com.estatecrm.customer_service.repository.CustomerCareRepository;
import com.estatecrm.customer_service.repository.EmailCareRepository;
import com.estatecrm.customer_service.repository.EmailTemplateRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Bao ve quy tac: chi ghi ban ghi SENT khi mail-service da nhan gui. */
@ExtendWith(MockitoExtension.class)
class EmailCareServiceTests {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID CARE_ID = UUID.randomUUID();
    private static final UUID EMAIL_ID = UUID.randomUUID();
    private static final String TO = "khach@example.com";

    @Mock
    private EmailCareRepository emailCareRepository;

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private CustomerCareRepository customerCareRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private MailClient mailClient;

    @InjectMocks
    private EmailCareService emailCareService;

    @Test
    void createSendsMailBeforeStoringSentRecord() {
        stubCare();
        stubSave();

        emailCareService.create(
                CUSTOMER_ID, CARE_ID,
                new CreateEmailCareRequest(null, TO, "Chao anh", "Noi dung", null),
                ACTOR, "SALES");

        EmailCare stored = savedEmailCare();
        verify(mailClient).send(TO, "Chao anh", "Noi dung");
        assertThat(stored.getStatus()).isEqualTo(EmailCareStatus.SENT);
        assertThat(stored.getSentAt()).isNotNull();
    }

    @Test
    void createDoesNotSendWhenMarkedFailed() {
        stubCare();
        stubSave();

        emailCareService.create(
                CUSTOMER_ID, CARE_ID,
                new CreateEmailCareRequest(null, TO, "Chao anh", "Noi dung", EmailCareStatus.FAILED),
                ACTOR, "SALES");

        EmailCare stored = savedEmailCare();
        verifyNoInteractions(mailClient);
        assertThat(stored.getStatus()).isEqualTo(EmailCareStatus.FAILED);
        assertThat(stored.getSentAt()).isNull();
    }

    @Test
    void createFallsBackToTemplateSubjectAndBody() {
        stubCare();
        stubSave();
        EmailTemplate template = new EmailTemplate();
        template.setId(UUID.randomUUID());
        template.setSubject("Mau subject");
        template.setBody("Mau body");
        when(emailTemplateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        emailCareService.create(
                CUSTOMER_ID, CARE_ID,
                new CreateEmailCareRequest(template.getId(), TO, null, null, null),
                ACTOR, "SALES");

        verify(mailClient).send(TO, "Mau subject", "Mau body");
    }

    @Test
    void updateToSentResendsMail() {
        EmailCare existing = emailCare(EmailCareStatus.FAILED);
        stubCare();
        when(emailCareRepository.findById(EMAIL_ID)).thenReturn(Optional.of(existing));
        stubSave();

        emailCareService.update(
                CUSTOMER_ID, EMAIL_ID, new UpdateEmailCareRequest(EmailCareStatus.SENT, null),
                ACTOR, "SALES");

        verify(mailClient).send(TO, "Chao anh", "Noi dung");
        assertThat(savedEmailCare().getSentAt()).isNotNull();
    }

    @Test
    void updateToOpenedDoesNotSend() {
        EmailCare existing = emailCare(EmailCareStatus.SENT);
        existing.setSentAt(java.time.LocalDateTime.now());
        stubCare();
        when(emailCareRepository.findById(EMAIL_ID)).thenReturn(Optional.of(existing));
        stubSave();

        emailCareService.update(
                CUSTOMER_ID, EMAIL_ID, new UpdateEmailCareRequest(EmailCareStatus.OPENED, null),
                ACTOR, "SALES");

        verify(mailClient, never()).send(any(), any(), any());
        assertThat(existing.getOpenedAt()).isNotNull();
    }

    private void stubCare() {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        CustomerCare care = new CustomerCare();
        care.setId(CARE_ID);
        care.setCustomer(customer);
        when(customerCareRepository.findById(CARE_ID)).thenReturn(Optional.of(care));
    }

    private void stubSave() {
        when(emailCareRepository.save(any(EmailCare.class))).thenAnswer(call -> call.getArgument(0));
    }

    private EmailCare emailCare(EmailCareStatus status) {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        CustomerCare care = new CustomerCare();
        care.setId(CARE_ID);
        care.setCustomer(customer);
        EmailCare emailCare = new EmailCare();
        emailCare.setId(EMAIL_ID);
        emailCare.setCustomerCare(care);
        emailCare.setToEmail(TO);
        emailCare.setSubject("Chao anh");
        emailCare.setBody("Noi dung");
        emailCare.setStatus(status);
        return emailCare;
    }

    private EmailCare savedEmailCare() {
        ArgumentCaptor<EmailCare> captor = ArgumentCaptor.forClass(EmailCare.class);
        verify(emailCareRepository).save(captor.capture());
        return captor.getValue();
    }
}
