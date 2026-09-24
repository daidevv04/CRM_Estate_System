package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.CreateLeadRequest;
import com.estatecrm.crm_service.dto.LeadResponse;
import com.estatecrm.crm_service.dto.LeadStageHistoryResponse;
import com.estatecrm.crm_service.dto.UpdateLeadRequest;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.service.LeadService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse create(
            @Valid @RequestBody CreateLeadRequest request, @AuthenticationPrincipal Jwt jwt) {
        return leadService.create(request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public Page<LeadResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) LeadStage stage,
            @RequestParam(required = false) LocalDate closeDate,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return leadService.list(
                customerId, productId, assignedTo, stage, closeDate,
                actorId(jwt), role(jwt), pageable);
    }

    @GetMapping("/{leadId}")
    public LeadResponse get(@PathVariable UUID leadId, @AuthenticationPrincipal Jwt jwt) {
        return leadService.get(leadId, actorId(jwt), role(jwt));
    }

    @GetMapping("/{leadId}/stage-history")
    public java.util.List<LeadStageHistoryResponse> history(
            @PathVariable UUID leadId, @AuthenticationPrincipal Jwt jwt) {
        return leadService.history(leadId, actorId(jwt), role(jwt));
    }

    @PatchMapping("/{leadId}")
    public LeadResponse update(
            @PathVariable UUID leadId,
            @Valid @RequestBody UpdateLeadRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return leadService.update(leadId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{leadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID leadId, @AuthenticationPrincipal Jwt jwt) {
        leadService.delete(leadId, actorId(jwt), role(jwt));
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
