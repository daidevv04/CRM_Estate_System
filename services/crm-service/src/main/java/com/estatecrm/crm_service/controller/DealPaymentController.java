package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.CreateDealPaymentRequest;
import com.estatecrm.crm_service.dto.DealPaymentResponse;
import com.estatecrm.crm_service.service.DealPaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cac dot thanh toan long duoi hop dong, append-only. */
@RestController
@RequestMapping("/deals/{dealId}/payments")
public class DealPaymentController {

    private final DealPaymentService dealPaymentService;

    public DealPaymentController(DealPaymentService dealPaymentService) {
        this.dealPaymentService = dealPaymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DealPaymentResponse create(
            @PathVariable UUID dealId,
            @Valid @RequestBody CreateDealPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return dealPaymentService.create(dealId, request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public List<DealPaymentResponse> list(@PathVariable UUID dealId, @AuthenticationPrincipal Jwt jwt) {
        return dealPaymentService.list(dealId, actorId(jwt), role(jwt));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
