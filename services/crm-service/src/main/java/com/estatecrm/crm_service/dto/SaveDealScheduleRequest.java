package com.estatecrm.crm_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SaveDealScheduleRequest(
    @NotEmpty List<@Valid PaymentScheduleItemDto> installments
) {}
