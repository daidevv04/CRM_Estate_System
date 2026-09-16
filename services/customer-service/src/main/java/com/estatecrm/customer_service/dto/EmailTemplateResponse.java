package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.entity.EmailTemplate;
import com.estatecrm.customer_service.enums.EmailTemplateCategory;
import com.estatecrm.customer_service.enums.EmailTemplateStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmailTemplateResponse(
        UUID id,
        String name,
        String subject,
        String body,
        EmailTemplateCategory category,
        EmailTemplateStatus status,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static EmailTemplateResponse from(EmailTemplate template) {
        return new EmailTemplateResponse(
                template.getId(),
                template.getName(),
                template.getSubject(),
                template.getBody(),
                template.getCategory(),
                template.getStatus(),
                template.getCreatedBy(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
