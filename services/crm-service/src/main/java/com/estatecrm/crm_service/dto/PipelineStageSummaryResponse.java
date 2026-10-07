package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.LeadStage;
import java.math.BigDecimal;

/** Snapshot lead hien tai + su kien chuyen stage trong ky cho bao cao funnel. */
public record PipelineStageSummaryResponse(
        LeadStage stage,
        long currentLeadCount,
        BigDecimal expectedValue,
        long enteredCount,
        long transitionFromPreviousCount,
        Double transitionRate) {
}