package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DisableTwoFactorRequest(
        @NotBlank String password,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}
