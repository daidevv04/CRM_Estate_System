package com.estatecrm.user_service.dto.auth;

import com.estatecrm.user_service.enums.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Xac thuc OTP. purpose=LOGIN tra ve token; purpose=RESET_PASSWORD dat luon mat
 * khau moi nen bat buoc co newPassword; purpose=VERIFY_EMAIL ghi moc xac thuc.
 */
public record VerifyOtpRequest(
        @NotBlank String usernameOrEmail,
        @NotNull OtpPurpose purpose,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
        @Size(min = 8, max = 100) String newPassword) {
}
