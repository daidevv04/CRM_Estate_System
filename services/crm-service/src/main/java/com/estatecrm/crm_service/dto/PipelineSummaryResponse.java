package com.estatecrm.crm_service.dto;

import java.util.List;

/** Ket qua GET /leads/pipeline-summary. */
public record PipelineSummaryResponse(List<PipelineStageSummaryResponse> stages) {
}