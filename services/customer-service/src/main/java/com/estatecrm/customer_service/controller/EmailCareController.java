package com.estatecrm.customer_service.controller;

import com.estatecrm.customer_service.dto.CreateEmailCareRequest;
import com.estatecrm.customer_service.dto.EmailCareResponse;
import com.estatecrm.customer_service.dto.UpdateEmailCareRequest;
import com.estatecrm.customer_service.enums.EmailCareStatus;
import com.estatecrm.customer_service.service.EmailCareService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email cua tung nhat ky cham soc, long duoi
 * /customers/{customerId}/cares/{careId}/emails.
 */
@RestController
@RequestMapping("/customers/{customerId}/cares/{careId}/emails")
public class EmailCareController {

    private final EmailCareService emailCareService;

    public EmailCareController(EmailCareService emailCareService) {
        this.emailCareService = emailCareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmailCareResponse create(
            @PathVariable UUID customerId,
            @PathVariable UUID careId,
            @Valid @RequestBody CreateEmailCareRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return emailCareService.create(customerId, careId, request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public Page<EmailCareResponse> list(
            @PathVariable UUID customerId,
            @PathVariable UUID careId,
            @RequestParam(required = false) EmailCareStatus status,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return emailCareService.listByCare(customerId, careId, status, actorId(jwt), role(jwt), pageable);
    }

    @GetMapping("/{emailId}")
    public EmailCareResponse get(
            @PathVariable UUID customerId,
            @PathVariable UUID careId,
            @PathVariable UUID emailId,
            @AuthenticationPrincipal Jwt jwt) {
        return emailCareService.get(customerId, emailId, actorId(jwt), role(jwt));
    }

    @PatchMapping("/{emailId}")
    public EmailCareResponse update(
            @PathVariable UUID customerId,
            @PathVariable UUID careId,
            @PathVariable UUID emailId,
            @Valid @RequestBody UpdateEmailCareRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return emailCareService.update(customerId, emailId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{emailId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID customerId,
            @PathVariable UUID careId,
            @PathVariable UUID emailId,
            @AuthenticationPrincipal Jwt jwt) {
        emailCareService.delete(customerId, emailId, actorId(jwt), role(jwt));
    }

    /** Lay id nguoi dang nhap tu claim subject cua JWT da verify. */
    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Lay vai tro tu claim "role" do user-service phat hanh. */
    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
