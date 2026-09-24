package com.estatecrm.mail_service.controller;

import com.estatecrm.mail_service.dto.SendMailRequest;
import com.estatecrm.mail_service.service.MailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API noi bo: chi user-service goi, bang internal token (xem SecurityConfig). */
@RestController
@RequestMapping("/emails")
public class MailController {

    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@Valid @RequestBody SendMailRequest request) {
        mailService.send(request);
    }
}
