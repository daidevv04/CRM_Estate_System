package com.estatecrm.customer_service.dto.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ban sao hop dong cua mail-service POST /emails. Giu rieng o day de
 * customer-service khong phu thuoc build vao module khac.
 */
public record SendMailRequest(
        @NotBlank @Email @Size(max = 100) String to,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String body) {
}
