package com.estatecrm.customer_service.controller;

import com.estatecrm.customer_service.dto.EmailCareResponse;
import com.estatecrm.customer_service.dto.SendCustomerEmailRequest;
import com.estatecrm.customer_service.service.EmailCareService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers/{customerId}/emails")
public class CustomerEmailController {

    private final EmailCareService emailCareService;

    public CustomerEmailController(EmailCareService emailCareService) {
        this.emailCareService = emailCareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmailCareResponse send(
            @PathVariable UUID customerId,
            @Valid @RequestBody SendCustomerEmailRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return emailCareService.sendToCustomer(customerId, request, actorId(jwt), role(jwt));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
