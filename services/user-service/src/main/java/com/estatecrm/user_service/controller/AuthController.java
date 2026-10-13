package com.estatecrm.user_service.controller;

import com.estatecrm.user_service.dto.auth.DisableTwoFactorRequest;
import com.estatecrm.user_service.dto.auth.EnableTwoFactorRequest;
import com.estatecrm.user_service.dto.auth.LoginRequest;
import com.estatecrm.user_service.dto.auth.PasswordResetConfirmRequest;
import com.estatecrm.user_service.dto.auth.PasswordResetRequest;
import com.estatecrm.user_service.dto.auth.PasswordResetVerificationResponse;
import com.estatecrm.user_service.dto.auth.PasswordResetVerifyRequest;
import com.estatecrm.user_service.dto.auth.RefreshTokenRequest;
import com.estatecrm.user_service.dto.auth.SendOtpRequest;
import com.estatecrm.user_service.dto.auth.TokenResponse;
import com.estatecrm.user_service.dto.auth.TwoFactorSetupResponse;
import com.estatecrm.user_service.dto.auth.TwoFactorVerifyRequest;
import com.estatecrm.user_service.dto.auth.UserSessionResponse;
import com.estatecrm.user_service.dto.auth.VerifyOtpRequest;
import com.estatecrm.user_service.enums.OtpPurpose;
import com.estatecrm.user_service.service.AuthService;
import com.estatecrm.user_service.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        return authService.login(request, clientIp(httpRequest), userAgent(httpRequest));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request, clientIp(httpRequest), userAgent(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    @GetMapping("/sessions")
    public List<UserSessionResponse> getSessions(@AuthenticationPrincipal Jwt jwt) {
        return authService.getActiveSessions(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("sid"));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        authService.revokeSession(UUID.fromString(jwt.getSubject()), id);
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOtherSessions(@AuthenticationPrincipal Jwt jwt) {
        authService.revokeOtherSessions(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("sid"));
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
        authService.requestPasswordReset(request.usernameOrEmail(), clientIp(httpRequest));
    }

    @PostMapping("/password-reset/verify")
    public PasswordResetVerificationResponse verifyPasswordReset(
            @Valid @RequestBody PasswordResetVerifyRequest request, HttpServletRequest httpRequest) {
        return authService.verifyPasswordReset(request, clientIp(httpRequest));
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
    }

    @PostMapping("/otp/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendOtp(@Valid @RequestBody SendOtpRequest request, HttpServletRequest httpRequest) {
        otpService.send(request.usernameOrEmail(), request.purpose(), clientIp(httpRequest));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<TokenResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        if (request.purpose() == OtpPurpose.LOGIN) {
            return ResponseEntity.ok(authService.verifyOtpLogin(request, clientIp(httpRequest), userAgent(httpRequest)));
        }
        if (request.purpose() == OtpPurpose.RESET_PASSWORD) {
            authService.verifyOtpResetPassword(request, clientIp(httpRequest));
        } else {
            authService.verifyOtpEmail(request, clientIp(httpRequest));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/verify")
    public TokenResponse verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequest request, HttpServletRequest httpRequest) {
        return authService.verifyTwoFactor(request, clientIp(httpRequest), userAgent(httpRequest));
    }

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

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua : "";
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
