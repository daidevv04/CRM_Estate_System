package com.estatecrm.crm_service.dto;

import java.util.List;

public record PipelineSummaryResponse(List<PipelineStageSummaryResponse> stages) {
}
