package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.DebtAgingDtos.DebtAgingSummaryResponse;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentRequest;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentResponse;
import com.estatecrm.crm_service.service.DebtAgingService;
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

@RestController
@RequestMapping("/deals")
public class DebtAgingController {

    private final DebtAgingService debtAgingService;

    public DebtAgingController(DebtAgingService debtAgingService) {
        this.debtAgingService = debtAgingService;
    }

    @GetMapping("/debt-aging")
    public DebtAgingSummaryResponse getDebtAgingSummary(@AuthenticationPrincipal Jwt jwt) {
        return debtAgingService.getDebtAgingSummary(actorId(jwt), role(jwt));
    }

    @PostMapping("/schedules/{scheduleId}/remind")
    @ResponseStatus(HttpStatus.OK)
    public RemindPaymentResponse remindPayment(
            @PathVariable UUID scheduleId,
            @RequestBody(required = false) RemindPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return debtAgingService.remindPayment(
                scheduleId,
                request != null ? request : new RemindPaymentRequest(null),
                actorId(jwt),
                role(jwt)
        );
    }

    private UUID actorId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) return null;
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String role(Jwt jwt) {
        return jwt != null ? jwt.getClaimAsString("role") : null;
    }
}
