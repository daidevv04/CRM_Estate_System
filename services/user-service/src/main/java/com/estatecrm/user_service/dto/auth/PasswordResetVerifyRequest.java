package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetVerifyRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}