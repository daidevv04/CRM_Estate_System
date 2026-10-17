package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.DealPaymentScheduleResponse;
import com.estatecrm.crm_service.dto.SaveDealScheduleRequest;
import com.estatecrm.crm_service.service.DealPaymentScheduleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deals/{dealId}/schedules")
public class DealPaymentScheduleController {

    private final DealPaymentScheduleService scheduleService;

    public DealPaymentScheduleController(DealPaymentScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<DealPaymentScheduleResponse> list(
            @PathVariable UUID dealId,
            @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.getSchedules(dealId, actorId(jwt), role(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public List<DealPaymentScheduleResponse> save(
            @PathVariable UUID dealId,
            @Valid @RequestBody SaveDealScheduleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.saveCustomSchedules(dealId, request, actorId(jwt), role(jwt));
    }

    @PostMapping("/apply-template")
    @ResponseStatus(HttpStatus.OK)
    public List<DealPaymentScheduleResponse> applyTemplate(
            @PathVariable UUID dealId,
            @RequestParam(defaultValue = DealPaymentScheduleService.TEMPLATE_CONSTRUCTION_7) String templateCode,
            @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.applyTemplate(dealId, templateCode, actorId(jwt), role(jwt));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
