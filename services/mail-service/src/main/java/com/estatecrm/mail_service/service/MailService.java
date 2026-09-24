package com.estatecrm.mail_service.service;

import com.estatecrm.mail_service.dto.SendMailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gui email qua SMTP. Cau hinh SMTP lay tu bien moi truong (spring.mail.*),
 * khong hardcode credential trong code.
 *
 * Loi SMTP duoc doi thanh 500 voi message chung: chi tiet loi cua nha cung cap
 * (co the chua thong tin tai khoan) khong duoc tra ra client.
 */
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(SendMailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.to());
        message.setSubject(request.subject());
        message.setText(request.body());
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Email delivery failed");
        }
    }
}
