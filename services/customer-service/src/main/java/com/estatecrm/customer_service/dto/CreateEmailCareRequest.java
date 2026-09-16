package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.EmailCareStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Ghi lai mot email da gui cho khach. customerCareId khong nam trong body vi da
 * co tren duong dan; de trong body se xung dot voi path. Neu co templateId va de
 * trong subject/body thi lay ban mau tu template.
 */
public record CreateEmailCareRequest(
        UUID templateId,
        @NotBlank @Email @Size(max = 100) String toEmail,
        @Size(max = 255) String subject,
        @Size(max = 20000) String body,
        EmailCareStatus status) {
}
