package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.entity.Appointment;
import com.estatecrm.customer_service.enums.AppointmentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID customerId,
        UUID salesId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String color,
        Integer reminderMinutes,
        AppointmentStatus status) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getCustomer().getId(),
                appointment.getSalesId(),
                appointment.getTitle(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getColor(),
                appointment.getReminderMinutes(),
                appointment.getStatus());
    }
}
