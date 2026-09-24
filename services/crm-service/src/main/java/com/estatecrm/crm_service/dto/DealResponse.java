package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DealResponse(
        UUID id,
        UUID leadId,
        UUID salesId,
        String contractCode,
        BigDecimal contractValue,
        BigDecimal depositAmount,
        LocalDate depositDate,
        LocalDate signedDate,
        PaymentStatus paymentStatus,
        String paymentMethod,
        ApprovalStatus approvalStatus,
        UUID approvedBy,
        LocalDateTime approvedAt,
        String fileUrl,
        DealStatus status,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DealResponse from(Deal deal) {
        return new DealResponse(
                deal.getId(),
                deal.getLeadId(),
                deal.getSalesId(),
                deal.getContractCode(),
                deal.getContractValue(),
                deal.getDepositAmount(),
                deal.getDepositDate(),
                deal.getSignedDate(),
                deal.getPaymentStatus(),
                deal.getPaymentMethod(),
                deal.getApprovalStatus(),
                deal.getApprovedBy(),
                deal.getApprovedAt(),
                deal.getFileUrl(),
                deal.getStatus(),
                deal.getNote(),
                deal.getCreatedAt(),
                deal.getUpdatedAt());
    }
}
