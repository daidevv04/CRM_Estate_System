package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "Password must contain a number")
        @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "Password must contain a special character")
        String newPassword) {
}