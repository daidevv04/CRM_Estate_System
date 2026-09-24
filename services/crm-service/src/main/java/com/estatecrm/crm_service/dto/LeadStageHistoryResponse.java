package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.LeadStageHistory;
import com.estatecrm.crm_service.enums.LeadStage;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeadStageHistoryResponse(
        UUID id,
        UUID leadId,
        LeadStage fromStage,
        LeadStage toStage,
        UUID changedBy,
        LocalDateTime changedAt) {

    public static LeadStageHistoryResponse from(LeadStageHistory item) {
        return new LeadStageHistoryResponse(
                item.getId(), item.getLeadId(), item.getFromStage(), item.getToStage(),
                item.getChangedBy(), item.getChangedAt());
    }
}
