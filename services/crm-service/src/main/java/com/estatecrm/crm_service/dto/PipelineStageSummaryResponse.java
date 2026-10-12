package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.LeadStage;
import java.math.BigDecimal;

public record PipelineStageSummaryResponse(
        LeadStage stage,
        long currentLeadCount,
        BigDecimal expectedValue,
        long enteredCount,
        long transitionFromPreviousCount,
        Double transitionRate) {
}
