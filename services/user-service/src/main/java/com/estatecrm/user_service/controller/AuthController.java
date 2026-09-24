package com.estatecrm.user_service.controller;

import com.estatecrm.user_service.dto.auth.DisableTwoFactorRequest;
import com.estatecrm.user_service.dto.auth.EnableTwoFactorRequest;
import com.estatecrm.user_service.dto.auth.LoginRequest;
import com.estatecrm.user_service.dto.auth.RefreshTokenRequest;
import com.estatecrm.user_service.dto.auth.SendOtpRequest;
import com.estatecrm.user_service.dto.auth.TokenResponse;
import com.estatecrm.user_service.dto.auth.TwoFactorSetupResponse;
import com.estatecrm.user_service.dto.auth.TwoFactorVerifyRequest;
import com.estatecrm.user_service.dto.auth.VerifyOtpRequest;
import com.estatecrm.user_service.enums.OtpPurpose;
import com.estatecrm.user_service.service.AuthService;
import com.estatecrm.user_service.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    public AuthController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    /**
     * Gui OTP qua email. Luon 204 du tai khoan khong ton tai (khong do duoc
     * tai khoan nao dang ky).
     */
    @PostMapping("/otp/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendOtp(@Valid @RequestBody SendOtpRequest request, HttpServletRequest httpRequest) {
        otpService.send(request.usernameOrEmail(), request.purpose(), clientIp(httpRequest));
    }

    /**
     * Xac thuc OTP. purpose=LOGIN tra ve token (dang nhap bang OTP);
     * purpose=RESET_PASSWORD dat mat khau moi va tra 204.
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<TokenResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        if (request.purpose() == OtpPurpose.LOGIN) {
            return ResponseEntity.ok(authService.verifyOtpLogin(request, clientIp(httpRequest)));
        }
        if (request.purpose() == OtpPurpose.RESET_PASSWORD) {
            authService.verifyOtpResetPassword(request, clientIp(httpRequest));
        } else {
            authService.verifyOtpEmail(request, clientIp(httpRequest));
        }
        return ResponseEntity.noContent().build();
    }

    /** Buoc 2 cua dang nhap: doi code TOTP lay token. */
    @PostMapping("/2fa/verify")
    public TokenResponse verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequest request, HttpServletRequest httpRequest) {
        return authService.verifyTwoFactor(request, clientIp(httpRequest));
    }

    /**
     * Bat 2FA. Schema khong co trang thai "cho xac nhan" (chk_users_2fa_secret
     * bat buoc secret di kem co bat), nen secret duoc luu va bat ngay; client
     * phai luu secret/QR truoc khi dang xuat.
     */
    @PostMapping("/2fa/enable")
    public TwoFactorSetupResponse enableTwoFactor(
            @Valid @RequestBody EnableTwoFactorRequest request, @AuthenticationPrincipal Jwt jwt) {
        return authService.enableTwoFactor(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTwoFactor(
            @Valid @RequestBody DisableTwoFactorRequest request, @AuthenticationPrincipal Jwt jwt) {
        authService.disableTwoFactor(UUID.fromString(jwt.getSubject()), request);
    }

    /**
     * IP dung cho throttle. Gateway chay trong cung docker network nen
     * getRemoteAddr() mac dinh la IP cua gateway, khong phai IP khach. Bat
     * server.forward-headers-strategy=native de Tomcat tu doc X-Forwarded-For
     * va tra ve IP khach, nhung chi khi request den tu proxy tin cay.
     *
     * Xem review_full_system_v2.md muc N1.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}