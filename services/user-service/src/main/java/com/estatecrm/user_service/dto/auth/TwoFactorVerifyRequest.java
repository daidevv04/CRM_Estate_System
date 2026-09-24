package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Buoc 2 cua dang nhap khi tai khoan da bat 2FA. */
public record TwoFactorVerifyRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}
