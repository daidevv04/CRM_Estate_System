package com.estatecrm.customer_service.dto.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMailRequest(
        @NotBlank @Email @Size(max = 100) String to,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String body) {
}
