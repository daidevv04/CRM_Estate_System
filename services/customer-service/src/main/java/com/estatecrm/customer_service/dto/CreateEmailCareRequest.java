package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.EmailCareStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateEmailCareRequest(
        UUID templateId,
        @NotBlank @Email @Size(max = 100) String toEmail,
        @Size(max = 255) String subject,
        @Size(max = 20000) String body,
        EmailCareStatus status) {
}
