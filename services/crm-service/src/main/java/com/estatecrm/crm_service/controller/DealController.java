package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.CreateDealRequest;
import com.estatecrm.crm_service.dto.DealResponse;
import com.estatecrm.crm_service.dto.UpdateDealRequest;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.PaymentStatus;
import com.estatecrm.crm_service.service.DealService;
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

@RestController
@RequestMapping("/deals")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DealResponse create(
            @Valid @RequestBody CreateDealRequest request, @AuthenticationPrincipal Jwt jwt) {
        return dealService.create(request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public Page<DealResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID salesId,
            @RequestParam(required = false) DealStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) ApprovalStatus approvalStatus,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return dealService.list(
                leadId, salesId, status, paymentStatus, approvalStatus,
                actorId(jwt), role(jwt), pageable);
    }

    @GetMapping("/{dealId}")
    public DealResponse get(@PathVariable UUID dealId, @AuthenticationPrincipal Jwt jwt) {
        return dealService.get(dealId, actorId(jwt), role(jwt));
    }

    @PatchMapping("/{dealId}")
    public DealResponse update(
            @PathVariable UUID dealId,
            @Valid @RequestBody UpdateDealRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return dealService.update(dealId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{dealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID dealId, @AuthenticationPrincipal Jwt jwt) {
        dealService.delete(dealId, actorId(jwt), role(jwt));
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
