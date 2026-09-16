package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.EmailTemplateCategory;
import com.estatecrm.customer_service.enums.EmailTemplateStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmailTemplateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 20000) String body,
        @NotNull EmailTemplateCategory category,
        EmailTemplateStatus status) {
}
