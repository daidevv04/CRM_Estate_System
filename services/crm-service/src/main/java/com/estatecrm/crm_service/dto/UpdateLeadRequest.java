package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.LeadStage;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateLeadRequest(
        UUID customerId,
        UUID projectId,
        UUID productId,
        String demandNotes,
        LeadStage stage,
        @PositiveOrZero BigDecimal expectedValue,
        LocalDate closeDate,
        UUID assignedTo) {
}
