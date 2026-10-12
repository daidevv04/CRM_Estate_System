package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.estatecrm.user_service.enums.OtpPurpose;

public record SendOtpRequest(
        @NotBlank String usernameOrEmail,
        @NotNull OtpPurpose purpose) {
}
