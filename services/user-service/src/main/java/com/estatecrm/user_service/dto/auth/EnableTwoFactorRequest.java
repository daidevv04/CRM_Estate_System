package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record EnableTwoFactorRequest(
        @NotBlank String password) {
}
