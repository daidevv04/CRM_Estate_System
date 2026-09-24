package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Bat 2FA: phai nhap lai mat khau hien tai truoc khi doi secret. */
public record EnableTwoFactorRequest(
        @NotBlank String password) {
}
