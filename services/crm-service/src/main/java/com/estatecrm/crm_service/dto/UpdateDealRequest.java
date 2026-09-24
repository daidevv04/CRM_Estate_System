package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.PaymentMethod;
import com.estatecrm.crm_service.enums.PaymentStatus;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** PATCH: chi field nao gui len thi doi; null (khong co annotation NotBlank/NotNull) = giu nguyen. */
public record UpdateDealRequest(
        @Size(max = 50) String contractCode,
        @PositiveOrZero BigDecimal contractValue,
        @PositiveOrZero BigDecimal depositAmount,
        LocalDate depositDate,
        LocalDate signedDate,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        ApprovalStatus approvalStatus,
        @Size(max = 500) String fileUrl,
        DealStatus status,
        String note,
        UUID salesId) {
}
