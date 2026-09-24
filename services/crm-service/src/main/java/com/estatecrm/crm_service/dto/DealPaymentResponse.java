package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.DealPayment;
import com.estatecrm.crm_service.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DealPaymentResponse(
        UUID id,
        UUID dealId,
        BigDecimal amount,
        LocalDateTime paidAt,
        PaymentMethod method,
        String receiptUrl,
        String note,
        UUID createdBy,
        LocalDateTime createdAt) {

    public static DealPaymentResponse from(DealPayment payment) {
        return new DealPaymentResponse(
                payment.getId(), payment.getDealId(), payment.getAmount(), payment.getPaidAt(),
                payment.getMethod(), payment.getReceiptUrl(), payment.getNote(),
                payment.getCreatedBy(), payment.getCreatedAt());
    }
}
