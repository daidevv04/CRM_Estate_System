 package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import com.estatecrm.crm_service.enums.PropertyDirection;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** PATCH: chi field nao gui len thi doi; null = giu nguyen. */
public record UpdateProductRequest(
        @Size(max = 50) String code,
        ProductType type,
        @Positive BigDecimal area,
        @Size(max = 20) String block,
        @PositiveOrZero BigDecimal price,
        @PositiveOrZero Integer bedroom,
        PropertyDirection direction,
        ProductStatus status) {
}
