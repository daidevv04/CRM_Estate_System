package com.estatecrm.crm_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class DebtAgingDtos {

    public enum AgingBucket {
        DUE_SOON,
        OVERDUE_1_30,
        OVERDUE_31_60,
        OVERDUE_60_PLUS
    }

    public record DebtAgingItemResponse(
            UUID scheduleId,
            UUID dealId,
            String contractCode,
            UUID leadId,
            UUID customerId,
            UUID productId,
            String productCode,
            UUID projectId,
            String projectName,
            UUID salesId,
            int installmentNo,
            String installmentName,
            BigDecimal amountDue,
            BigDecimal amountPaid,
            BigDecimal amountRemaining,
            LocalDate dueDate,
            long daysDiff,
            AgingBucket agingBucket,
            LocalDateTime lastRemindedAt
    ) {}

    public record DebtAgingSummaryResponse(
            BigDecimal totalReceivable,
            BigDecimal collectedThisMonth,
            BigDecimal expectedThisMonth,
            BigDecimal dueSoonAmount,
            BigDecimal overdueAmount,
            int overdueCount,
            List<DebtAgingItemResponse> items
    ) {}

    public record RemindPaymentRequest(
            String note
    ) {}

    public record RemindPaymentResponse(
            boolean success,
            UUID scheduleId,
            UUID dealId,
            String contractCode,
            UUID salesId,
            boolean inAppSent,
            boolean emailSent,
            LocalDateTime remindedAt,
            String message
    ) {}
}
