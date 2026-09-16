package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.entity.EmailCare;
import com.estatecrm.customer_service.enums.EmailCareStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmailCareResponse(
        UUID id,
        UUID customerCareId,
        UUID templateId,
        String toEmail,
        String subject,
        String body,
        EmailCareStatus status,
        LocalDateTime sentAt,
        LocalDateTime openedAt,
        UUID createdBy,
        LocalDateTime createdAt) {

    public static EmailCareResponse from(EmailCare care) {
        return new EmailCareResponse(
                care.getId(),
                care.getCustomerCare().getId(),
                care.getTemplate() == null ? null : care.getTemplate().getId(),
                care.getToEmail(),
                care.getSubject(),
                care.getBody(),
                care.getStatus(),
                care.getSentAt(),
                care.getOpenedAt(),
                care.getCreatedBy(),
                care.getCreatedAt());
    }
}
