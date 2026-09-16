package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.CareType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCustomerCareRequest(
        @NotNull CareType type,
        @NotBlank @Size(max = 5000) String content) {
}
