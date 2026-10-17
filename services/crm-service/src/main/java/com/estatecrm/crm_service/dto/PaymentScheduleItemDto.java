package com.estatecrm.crm_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentScheduleItemDto(
    int installmentNo,
    @NotBlank String name,
    BigDecimal percentage,
    @NotNull @DecimalMin("0.0") BigDecimal amountDue,
    @NotNull LocalDate dueDate,
    String notes
) {}
