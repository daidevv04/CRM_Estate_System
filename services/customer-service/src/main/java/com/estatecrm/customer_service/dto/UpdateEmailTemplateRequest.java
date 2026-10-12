package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.EmailTemplateCategory;
import com.estatecrm.customer_service.enums.EmailTemplateStatus;
import jakarta.validation.constraints.Size;

public record UpdateEmailTemplateRequest(
        @Size(max = 100) String name,
        @Size(max = 255) String subject,
        @Size(max = 20000) String body,
        EmailTemplateCategory category,
        EmailTemplateStatus status) {
}
