package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateDealPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull @PastOrPresent LocalDateTime paidAt,
        PaymentMethod method,
        @Size(max = 500) String receiptUrl,
        String note) {
}
