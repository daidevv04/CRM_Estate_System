package com.estatecrm.crm_service.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LockProductRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,
        Integer days,
        LocalDate expiryDate,
        BigDecimal depositAmount,
        String notes) {
}
