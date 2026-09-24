package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.enums.LeadStage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        UUID customerId,
        UUID productId,
        LeadStage stage,
        BigDecimal expectedValue,
        LocalDate closeDate,
        UUID assignedTo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static LeadResponse from(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getCustomerId(),
                lead.getProductId(),
                lead.getStage(),
                lead.getExpectedValue(),
                lead.getCloseDate(),
                lead.getAssignedTo(),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }
}
