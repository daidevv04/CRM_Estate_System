package com.estatecrm.crm_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record UpdateContactDetailRequest(
        @PositiveOrZero BigDecimal unitPrice,
        @Min(1) Integer quantity,
        String note) {
}
