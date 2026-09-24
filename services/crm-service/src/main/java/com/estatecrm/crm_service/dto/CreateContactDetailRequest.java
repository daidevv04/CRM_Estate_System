package com.estatecrm.crm_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateContactDetailRequest(
        @NotNull UUID productId,
        @PositiveOrZero BigDecimal unitPrice,
        @Min(1) Integer quantity,
        String note) {
}
