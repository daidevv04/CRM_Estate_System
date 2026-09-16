package com.estatecrm.customer_service.controller;

import com.estatecrm.customer_service.dto.CreateEmailTemplateRequest;
import com.estatecrm.customer_service.dto.EmailTemplateResponse;
import com.estatecrm.customer_service.dto.UpdateEmailTemplateRequest;
import com.estatecrm.customer_service.enums.EmailTemplateCategory;
import com.estatecrm.customer_service.enums.EmailTemplateStatus;
import com.estatecrm.customer_service.service.EmailTemplateService;
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
 * Kho mau email. Doc: moi vai tro. Tao/sua/xoa: ADMIN/MANAGER (chan o service).
 */
@RestController
@RequestMapping("/email-templates")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    public EmailTemplateController(EmailTemplateService emailTemplateService) {
        this.emailTemplateService = emailTemplateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmailTemplateResponse create(
            @Valid @RequestBody CreateEmailTemplateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return emailTemplateService.create(request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public Page<EmailTemplateResponse> list(
            @RequestParam(required = false) EmailTemplateCategory category,
            @RequestParam(required = false) EmailTemplateStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return emailTemplateService.list(category, status, keyword, pageable);
    }

    @GetMapping("/{templateId}")
    public EmailTemplateResponse get(@PathVariable UUID templateId) {
        return emailTemplateService.get(templateId);
    }

    @PatchMapping("/{templateId}")
    public EmailTemplateResponse update(
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateEmailTemplateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return emailTemplateService.update(templateId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID templateId, @AuthenticationPrincipal Jwt jwt) {
        emailTemplateService.delete(templateId, actorId(jwt), role(jwt));
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
