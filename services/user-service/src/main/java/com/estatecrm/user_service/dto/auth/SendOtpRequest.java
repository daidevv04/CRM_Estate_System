package com.estatecrm.user_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.estatecrm.user_service.enums.OtpPurpose;

/**
 * Yeu cau gui OTP qua email.
 *
 * VERIFY_EMAIL chua duoc ho tro: bang users khong co cot xac thuc email nen
 * khong the ghi nhan ket qua. Xem OtpService.requireSupported.
 */
public record SendOtpRequest(
        @NotBlank String usernameOrEmail,
        @NotNull OtpPurpose purpose) {
}
