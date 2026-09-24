package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank String password,
        /** Bat buoc khi tai khoan da bat 2FA; bo qua khi 2FA dang tat. */
        String totpCode) {
}