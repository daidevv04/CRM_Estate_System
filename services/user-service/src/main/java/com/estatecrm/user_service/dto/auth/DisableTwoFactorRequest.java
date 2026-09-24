package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Tat 2FA: can ca mat khau va code TOTP dang hieu luc. */
public record DisableTwoFactorRequest(
        @NotBlank String password,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}
