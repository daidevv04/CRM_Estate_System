package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.AppointmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @Size(max = 10) String color,
        @PositiveOrZero Integer reminderMinutes,
        UUID salesId,
        AppointmentStatus status) {
}
