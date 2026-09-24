package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.ContactDetailResponse;
import com.estatecrm.crm_service.dto.CreateContactDetailRequest;
import com.estatecrm.crm_service.dto.UpdateContactDetailRequest;
import com.estatecrm.crm_service.service.ContactDetailService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Dong san pham nam duoi /deals/{dealId}/items de khop route gateway /api/deals/**. */
@RestController
@RequestMapping("/deals/{dealId}/items")
public class ContactDetailController {

    private final ContactDetailService contactDetailService;

    public ContactDetailController(ContactDetailService contactDetailService) {
        this.contactDetailService = contactDetailService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactDetailResponse create(
            @PathVariable UUID dealId,
            @Valid @RequestBody CreateContactDetailRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return contactDetailService.create(dealId, request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public List<ContactDetailResponse> list(
            @PathVariable UUID dealId, @AuthenticationPrincipal Jwt jwt) {
        return contactDetailService.list(dealId, actorId(jwt), role(jwt));
    }

    @PatchMapping("/{itemId}")
    public ContactDetailResponse update(
            @PathVariable UUID dealId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateContactDetailRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return contactDetailService.update(dealId, itemId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID dealId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        contactDetailService.delete(dealId, itemId, actorId(jwt), role(jwt));
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
