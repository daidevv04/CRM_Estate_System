package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import com.estatecrm.crm_service.enums.PropertyDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 50) String code,
        @NotNull ProductType type,
        @Positive BigDecimal area,
        @Size(max = 20) String block,
        @PositiveOrZero BigDecimal price,
        @PositiveOrZero Integer bedroom,
        PropertyDirection direction,
        ProductStatus status) {
}
