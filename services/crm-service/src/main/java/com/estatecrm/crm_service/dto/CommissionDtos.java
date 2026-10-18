package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.DealCommission;
import com.estatecrm.crm_service.entity.DealCommissionMilestone;
import com.estatecrm.crm_service.entity.ProjectCommissionPolicy;
import com.estatecrm.crm_service.enums.CommissionMilestoneStatus;
import com.estatecrm.crm_service.enums.CommissionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CommissionDtos {

    public record MilestoneRuleDto(
            int milestoneNo,
            String name,
            BigDecimal customerPaidThresholdPct,
            BigDecimal payoutPercentage
    ) {}

    public record CommissionSummaryDto(
            BigDecimal totalExpected,
            BigDecimal totalPaid,
            BigDecimal totalEligible,
            BigDecimal totalPending
    ) {}

    public record DealCommissionMilestoneResponse(
            UUID id,
            UUID dealCommissionId,
            int milestoneNo,
            String name,
            BigDecimal customerPaidThresholdPct,
            BigDecimal payoutPercentage,
            BigDecimal amountExpected,
            BigDecimal amountPaid,
            CommissionMilestoneStatus status,
            UUID approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime paidAt,
            String note,
            LocalDateTime createdAt
    ) {
        public static DealCommissionMilestoneResponse from(DealCommissionMilestone m) {
            return new DealCommissionMilestoneResponse(
                    m.getId(),
                    m.getDealCommissionId(),
                    m.getMilestoneNo(),
                    m.getName(),
                    m.getCustomerPaidThresholdPct(),
                    m.getPayoutPercentage(),
                    m.getAmountExpected(),
                    m.getAmountPaid(),
                    m.getStatus(),
                    m.getApprovedBy(),
                    m.getApprovedAt(),
                    m.getPaidAt(),
                    m.getNote(),
                    m.getCreatedAt()
            );
        }
    }

    public record DealCommissionResponse(
            UUID id,
            UUID dealId,
            UUID salesId,
            BigDecimal contractValue,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal bonusAmount,
            BigDecimal totalExpected,
            BigDecimal totalPaid,
            CommissionStatus status,
            LocalDateTime createdAt,
            List<DealCommissionMilestoneResponse> milestones
    ) {
        public static DealCommissionResponse from(DealCommission c, List<DealCommissionMilestone> milestones) {
            return new DealCommissionResponse(
                    c.getId(),
                    c.getDealId(),
                    c.getSalesId(),
                    c.getContractValue(),
                    c.getCommissionRate(),
                    c.getCommissionAmount(),
                    c.getBonusAmount() != null ? c.getBonusAmount() : BigDecimal.ZERO,
                    c.getTotalExpected(),
                    c.getTotalPaid() != null ? c.getTotalPaid() : BigDecimal.ZERO,
                    c.getStatus(),
                    c.getCreatedAt(),
                    milestones != null ? milestones.stream().map(DealCommissionMilestoneResponse::from).toList() : List.of()
            );
        }
    }

    public record ProjectCommissionPolicyResponse(
            UUID id,
            UUID projectId,
            BigDecimal commissionRate,
            BigDecimal defaultBonusAmount,
            String milestoneRules,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ProjectCommissionPolicyResponse from(ProjectCommissionPolicy p) {
            return new ProjectCommissionPolicyResponse(
                    p.getId(),
                    p.getProjectId(),
                    p.getCommissionRate(),
                    p.getDefaultBonusAmount() != null ? p.getDefaultBonusAmount() : BigDecimal.ZERO,
                    p.getMilestoneRules(),
                    p.getCreatedAt(),
                    p.getUpdatedAt()
            );
        }
    }

    public record UpdateCommissionPolicyRequest(
            BigDecimal commissionRate,
            BigDecimal defaultBonusAmount,
            String milestoneRules
    ) {}

    public record PayoutMilestoneRequest(
            String note
    ) {}
}
