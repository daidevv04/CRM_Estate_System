package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.CommissionDtos.ProjectCommissionPolicyResponse;
import com.estatecrm.crm_service.dto.CommissionDtos.UpdateCommissionPolicyRequest;
import com.estatecrm.crm_service.service.DealCommissionService;
import com.estatecrm.crm_service.service.DealService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/projects/{projectId}/commission-policy")
public class CommissionPolicyController {

    private final DealCommissionService commissionService;
    private final DealService dealService;

    public CommissionPolicyController(DealCommissionService commissionService, DealService dealService) {
        this.commissionService = commissionService;
        this.dealService = dealService;
    }

    @GetMapping
    public ProjectCommissionPolicyResponse getPolicy(
            @PathVariable UUID projectId) {
        return commissionService.getOrCreateProjectPolicy(projectId);
    }

    @PutMapping
    public ProjectCommissionPolicyResponse updatePolicy(
            @PathVariable UUID projectId,
            @RequestBody UpdateCommissionPolicyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (!dealService.isPrivileged(role(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản lý / Quản trị viên mới có quyền thiết lập chính sách hoa hồng");
        }
        return commissionService.updateProjectPolicy(projectId, request);
    }

    private String role(Jwt jwt) {
        return (String) jwt.getClaims().getOrDefault("role", "");
    }
}
