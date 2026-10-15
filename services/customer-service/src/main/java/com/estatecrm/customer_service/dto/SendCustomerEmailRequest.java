package com.estatecrm.customer_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendCustomerEmailRequest(
        UUID templateId,
        @Email @Size(max = 100) String toEmail,
        @Size(max = 255) String subject,
        @Size(max = 20000) String body,
        @Size(max = 500) String note) {
}
