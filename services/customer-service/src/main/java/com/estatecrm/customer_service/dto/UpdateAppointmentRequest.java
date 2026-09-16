package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.AppointmentStatus;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateAppointmentRequest(
        @Size(max = 200) String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        @Size(max = 10) String color,
        @PositiveOrZero Integer reminderMinutes,
        UUID salesId,
        AppointmentStatus status) {
}
