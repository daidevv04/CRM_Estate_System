package com.estatecrm.user_service.dto.auth;

import com.estatecrm.user_service.enums.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank String usernameOrEmail,
        @NotNull OtpPurpose purpose,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
        @Size(min = 8, max = 100) String newPassword) {
}
