package com.estatecrm.crm_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** PATCH: san pham dau tien khong doi; chi sua gia/dong luong/ghi chu. */
public record UpdateContactDetailRequest(
        @PositiveOrZero BigDecimal unitPrice,
        @Min(1) Integer quantity,
        String note) {
}
