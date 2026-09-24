package com.estatecrm.mail_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.estatecrm.mail_service.dto.SendMailRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

class MailServiceTests {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailService mailService = new MailService(mailSender, "crm@estate.local");

    @Test
    void sendsMessageWithConfiguredSender() {
        mailService.send(new SendMailRequest("sales@estate.local", "Ma OTP", "Ma cua ban la 123456"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("crm@estate.local");
        assertThat(message.getTo()).containsExactly("sales@estate.local");
        assertThat(message.getSubject()).isEqualTo("Ma OTP");
        assertThat(message.getText()).isEqualTo("Ma cua ban la 123456");
    }

    @Test
    void smtpFailureBecomesInternalErrorWithoutLeakingDetails() {
        doThrow(new MailSendException("550 mailbox unavailable for sales@estate.local"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> mailService.send(new SendMailRequest("sales@estate.local", "s", "b")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email delivery failed");
    }
}
