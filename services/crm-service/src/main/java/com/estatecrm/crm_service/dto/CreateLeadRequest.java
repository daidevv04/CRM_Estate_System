package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.LeadStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLeadRequest(
        @NotNull UUID customerId,
        @NotNull UUID productId,
        LeadStage stage,
        @PositiveOrZero BigDecimal expectedValue,
        LocalDate closeDate,
        UUID assignedTo) {
}
