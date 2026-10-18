package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.CommissionDtos.DealCommissionMilestoneResponse;
import com.estatecrm.crm_service.dto.CommissionDtos.DealCommissionResponse;
import com.estatecrm.crm_service.dto.CommissionDtos.PayoutMilestoneRequest;
import com.estatecrm.crm_service.service.DealCommissionService;
import com.estatecrm.crm_service.service.DealService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CommissionController {

    private final DealCommissionService commissionService;
    private final DealService dealService;

    public CommissionController(DealCommissionService commissionService, DealService dealService) {
        this.commissionService = commissionService;
        this.dealService = dealService;
    }

    @GetMapping("/commissions/my")
    public Map<String, Object> getMyCommissions(@AuthenticationPrincipal Jwt jwt) {
        return commissionService.getMyCommissions(actorId(jwt));
    }

    @GetMapping("/commissions")
    public Map<String, Object> getAllCommissions(@AuthenticationPrincipal Jwt jwt) {
        if (!dealService.isPrivileged(role(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản lý / Quản trị viên mới được xem danh sách hoa hồng toàn bộ");
        }
        return commissionService.getAllCommissions();
    }

    @PostMapping("/commissions/backfill")
    public Map<String, Object> backfillCommissions(@AuthenticationPrincipal Jwt jwt) {
        if (!dealService.isPrivileged(role(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản lý / Quản trị viên mới được thao tác");
        }
        int count = commissionService.backfillCommissionsForExistingDeals();
        return Map.of("backfilledCount", count);
    }

    @GetMapping("/deals/{dealId}/commission")
    public DealCommissionResponse getDealCommission(
            @PathVariable UUID dealId,
            @AuthenticationPrincipal Jwt jwt) {
        dealService.requireAccess(dealId, actorId(jwt), role(jwt));
        return commissionService.getCommissionByDealId(dealId);
    }

    @PostMapping("/commissions/milestones/{milestoneId}/approve")
    public DealCommissionMilestoneResponse approveMilestone(
            @PathVariable UUID milestoneId,
            @AuthenticationPrincipal Jwt jwt) {
        if (!dealService.isPrivileged(role(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản lý / Quản trị viên mới có quyền phê duyệt đợt giải ngân hoa hồng");
        }
        return commissionService.approveMilestone(milestoneId, actorId(jwt));
    }

    @PostMapping("/commissions/milestones/{milestoneId}/payout")
    public DealCommissionMilestoneResponse payoutMilestone(
            @PathVariable UUID milestoneId,
            @RequestBody(required = false) PayoutMilestoneRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (!dealService.isPrivileged(role(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản lý / Kế toán mới có quyền chi trả đợt hoa hồng");
        }
        String note = request != null ? request.note() : null;
        return commissionService.payoutMilestone(milestoneId, actorId(jwt), note);
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String role(Jwt jwt) {
        return (String) jwt.getClaims().getOrDefault("role", "");
    }
}
