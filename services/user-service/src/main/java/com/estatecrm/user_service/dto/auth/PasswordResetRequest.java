package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(@NotBlank String usernameOrEmail) {
}