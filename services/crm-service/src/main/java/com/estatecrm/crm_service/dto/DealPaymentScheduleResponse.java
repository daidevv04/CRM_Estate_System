package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.ScheduleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DealPaymentScheduleResponse(
    UUID id,
    UUID dealId,
    int installmentNo,
    String name,
    BigDecimal percentage,
    BigDecimal amountDue,
    LocalDate dueDate,
    String notes,
    BigDecimal amountPaid,
    BigDecimal amountRemaining,
    ScheduleStatus status,
    Long daysDiff
) {}
