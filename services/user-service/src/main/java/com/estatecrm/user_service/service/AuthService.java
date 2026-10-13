package com.estatecrm.user_service.service;

import com.estatecrm.user_service.dto.auth.DisableTwoFactorRequest;
import com.estatecrm.user_service.dto.auth.EnableTwoFactorRequest;
import com.estatecrm.user_service.dto.auth.LoginRequest;
import com.estatecrm.user_service.dto.auth.PasswordResetConfirmRequest;
import com.estatecrm.user_service.dto.auth.PasswordResetVerificationResponse;
import com.estatecrm.user_service.dto.auth.PasswordResetVerifyRequest;
import com.estatecrm.user_service.dto.auth.RefreshTokenRequest;
import com.estatecrm.user_service.dto.auth.TokenResponse;
import com.estatecrm.user_service.dto.auth.TwoFactorSetupResponse;
import com.estatecrm.user_service.dto.auth.TwoFactorVerifyRequest;
import com.estatecrm.user_service.dto.auth.UserSessionResponse;
import com.estatecrm.user_service.dto.auth.VerifyOtpRequest;
import com.estatecrm.user_service.entity.RefreshToken;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.repository.RefreshTokenRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(10);
    private static final String PASSWORD_RESET_PURPOSE = "password-reset";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final LoginAttemptService loginAttemptService;
    private final OtpService otpService;
    private final TotpService totpService;
    private final String dummyHash;
    private final String otpIssuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final int maxLoginAttempts;
    private final int maxLoginAttemptsIp;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            LoginAttemptService loginAttemptService,
            OtpService otpService,
            TotpService totpService,
            @Value("${app.auth.dummy-bcrypt-hash}") String dummyHash,
            @Value("${app.auth.otp-issuer:EstateCRM}") String otpIssuer,
            @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl,
            @Value("${app.auth.max-login-attempts:5}") int maxLoginAttempts,
            @Value("${app.auth.max-login-attempts-ip:50}") int maxLoginAttemptsIp) {

        String decodedDummy = new String(Base64.getDecoder().decode(dummyHash), StandardCharsets.UTF_8);
        if (!decodedDummy.startsWith("$2")) {
            throw new IllegalStateException("app.auth.dummy-bcrypt-hash must be base64 of a bcrypt hash");
        }
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.loginAttemptService = loginAttemptService;
        this.otpService = otpService;
        this.totpService = totpService;
        this.dummyHash = decodedDummy;
        this.otpIssuer = otpIssuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.maxLoginAttempts = maxLoginAttempts;
        this.maxLoginAttemptsIp = maxLoginAttemptsIp;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp) {
        return login(request, clientIp, null);
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp, String userAgent) {
        String identifier = request.usernameOrEmail().trim();
        String accountKey = "account:" + identifier.toLowerCase(Locale.ROOT);
        String ipKey = "ip:" + clientIp;
        loginAttemptService.checkBlocked(accountKey, maxLoginAttempts);
        loginAttemptService.checkBlocked(ipKey, maxLoginAttemptsIp);

        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier.toLowerCase(Locale.ROOT)))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            if (user == null) {
                passwordEncoder.matches(request.password(), dummyHash);
            }
            loginAttemptService.recordFailure(accountKey);
            loginAttemptService.recordFailure(ipKey);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(FORBIDDEN, "User is not active");
        }
        requireSecondFactor(user, request.totpCode(), accountKey, ipKey);

        loginAttemptService.reset(accountKey);
        loginAttemptService.reset(ipKey);
        return issueTokens(user, Instant.now(), LocalDateTime.now(), clientIp, parseDeviceName(userAgent));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse refresh(RefreshTokenRequest request) {
        return refresh(request, null, null);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse refresh(RefreshTokenRequest request, String clientIp, String userAgent) {
        String refreshToken = request.refreshToken();
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(refreshToken);
        } catch (JwtException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
        }
        if (!"refresh".equals(jwt.getClaimAsString("token_type"))) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
        }

        String tokenHash = sha256(refreshToken);
        Instant now = Instant.now();
        LocalDateTime databaseNow = LocalDateTime.now();

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));

        if (refreshTokenRepository.tryRevoke(tokenHash, databaseNow) == 0) {
            if (storedToken.getRevokedAt() != null) {
                refreshTokenRepository.revokeAllForUser(storedToken.getUser().getId(), databaseNow);
            }
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
        }

        User user = storedToken.getUser();
        if (!user.getId().toString().equals(jwt.getSubject()) || user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(FORBIDDEN, "User is not active");
        }

        String deviceName = (userAgent != null && !userAgent.isBlank())
                ? parseDeviceName(userAgent)
                : (storedToken.getDeviceName() != null ? storedToken.getDeviceName() : "Thiết bị không xác định");
        String ip = (clientIp != null && !clientIp.isBlank())
                ? clientIp
                : storedToken.getIpAddress();

        return issueTokens(user, now, databaseNow, ip, deviceName);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.tryRevoke(sha256(request.refreshToken()), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<UserSessionResponse> getActiveSessions(UUID userId, String currentSidStr) {
        LocalDateTime now = LocalDateTime.now();
        List<RefreshToken> tokens = refreshTokenRepository.findActiveByUserId(userId, now);
        return tokens.stream().map(t -> new UserSessionResponse(
                t.getId(),
                t.getDeviceName() != null ? t.getDeviceName() : "Thiết bị không xác định",
                t.getIpAddress(),
                t.getCreatedAt(),
                t.getLastUsedAt(),
                currentSidStr != null && currentSidStr.equals(t.getId().toString())
        )).toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        int updated = refreshTokenRepository.revokeByIdAndUserId(sessionId, userId, LocalDateTime.now());
        if (updated == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Session not found or already revoked");
        }
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, String currentSidStr) {
        LocalDateTime now = LocalDateTime.now();
        if (currentSidStr != null && !currentSidStr.isBlank()) {
            try {
                UUID currentSid = UUID.fromString(currentSidStr);
                refreshTokenRepository.revokeAllExcept(userId, currentSid, now);
                return;
            } catch (IllegalArgumentException ignored) {
            }
        }
        refreshTokenRepository.revokeAllForUser(userId, now);
    }

    private TokenResponse issueTokens(User user, Instant now, LocalDateTime databaseNow) {
        return issueTokens(user, now, databaseNow, null, "Thiết bị không xác định");
    }

    private TokenResponse issueTokens(
            User user, Instant now, LocalDateTime databaseNow, String ipAddress, String deviceName) {
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        String refreshToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("user-service")
                        .issuedAt(now)
                        .expiresAt(refreshExpiresAt)
                        .subject(user.getId().toString())
                        .id(UUID.randomUUID().toString())
                        .claim("token_type", "refresh")
                        .build())).getTokenValue();

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setTokenHash(sha256(refreshToken));
        storedToken.setExpiresAt(databaseNow.plus(refreshTokenTtl));
        storedToken.setIpAddress(ipAddress);
        storedToken.setDeviceName(deviceName);
        storedToken.setLastUsedAt(databaseNow);
        storedToken = refreshTokenRepository.save(storedToken);

        Instant accessExpiresAt = now.plus(accessTokenTtl);
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("user-service")
                        .issuedAt(now)
                        .expiresAt(accessExpiresAt)
                        .subject(user.getId().toString())
                        .claim("sid", storedToken.getId().toString())
                        .claim("username", user.getUsername())
                        .claim("role", user.getRole().name())
                        .build())).getTokenValue();

        return new TokenResponse(
                accessToken, refreshToken, "Bearer", accessTokenTtl.toSeconds());
    }

    public static String parseDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Thiết bị không xác định";
        }
        String uaLower = userAgent.toLowerCase(Locale.ROOT);
        if (uaLower.contains("postman")) {
            return "Postman";
        }
        if (uaLower.contains("curl")) {
            return "cURL";
        }

        String os = "Thiết bị khác";
        if (uaLower.contains("windows")) {
            os = "Windows";
        } else if (uaLower.contains("iphone")) {
            os = "iPhone";
        } else if (uaLower.contains("ipad")) {
            os = "iPad";
        } else if (uaLower.contains("android")) {
            os = "Android";
        } else if (uaLower.contains("macintosh") || uaLower.contains("mac os")) {
            os = "macOS";
        } else if (uaLower.contains("linux")) {
            os = "Linux";
        }

        String browser = "Browser";
        if (userAgent.contains("Edg/")) {
            browser = "Edge";
        } else if (userAgent.contains("Chrome/") && !userAgent.contains("Edg/")) {
            browser = "Chrome";
        } else if (userAgent.contains("Safari/") && !userAgent.contains("Chrome/")) {
            browser = "Safari";
        } else if (userAgent.contains("Firefox/")) {
            browser = "Firefox";
        } else if (userAgent.contains("OPR/") || userAgent.contains("Opera/")) {
            browser = "Opera";
        }

        return browser + " (" + os + ")";
    }

    @Transactional
    public TokenResponse verifyTwoFactor(TwoFactorVerifyRequest request, String clientIp) {
        return verifyTwoFactor(request, clientIp, null);
    }

    @Transactional
    public TokenResponse verifyTwoFactor(TwoFactorVerifyRequest request, String clientIp, String userAgent) {
        String identifier = request.usernameOrEmail().trim();
        String accountKey = "account:" + identifier.toLowerCase(Locale.ROOT);
        String ipKey = "ip:" + clientIp;
        loginAttemptService.checkBlocked(accountKey, maxLoginAttempts);
        loginAttemptService.checkBlocked(ipKey, maxLoginAttempts);

        User user = findUser(identifier);
        if (user.getStatus() != UserStatus.ACTIVE || !user.isTwoFactorEnabled()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid TOTP code");
        }
        requireSecondFactor(user, request.code(), accountKey, ipKey);
        loginAttemptService.reset(accountKey);
        loginAttemptService.reset(ipKey);
        return issueTokens(user, Instant.now(), LocalDateTime.now(), clientIp, parseDeviceName(userAgent));
    }

    @Transactional
    public TwoFactorSetupResponse enableTwoFactor(UUID userId, EnableTwoFactorRequest request) {
        User user = findById(userId);
        requirePassword(user, request.password());
        String secret = totpService.generateSecret();
        user.setTwoFactorSecret(secret);
        user.setTwoFactorEnabled(true);
        user.setUpdatedBy(user);
        return new TwoFactorSetupResponse(secret, totpService.otpauthUri(otpIssuer, user.getUsername(), secret));
    }

    @Transactional
    public void disableTwoFactor(UUID userId, DisableTwoFactorRequest request) {
        User user = findById(userId);
        requirePassword(user, request.password());
        if (!user.isTwoFactorEnabled() || !totpService.verify(user.getTwoFactorSecret(), request.code())) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid TOTP code");
        }
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabled(false);
        user.setUpdatedBy(user);
    }

    @Transactional
    public TokenResponse verifyOtpLogin(VerifyOtpRequest request, String clientIp) {
        return verifyOtpLogin(request, clientIp, null);
    }

    @Transactional
    public TokenResponse verifyOtpLogin(VerifyOtpRequest request, String clientIp, String userAgent) {
        User user = otpService.verify(request.usernameOrEmail(), request.purpose(), request.code(), clientIp);
        return issueTokens(user, Instant.now(), LocalDateTime.now(), clientIp, parseDeviceName(userAgent));
    }

    @Transactional
    public void verifyOtpResetPassword(VerifyOtpRequest request, String clientIp) {
        if (request.newPassword() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "newPassword is required for RESET_PASSWORD");
        }
        User user = otpService.verify(request.usernameOrEmail(), request.purpose(), request.code(), clientIp);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedBy(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now());
    }

    @Transactional
    public void requestPasswordReset(String usernameOrEmail, String clientIp) {
        otpService.send(usernameOrEmail, com.estatecrm.user_service.enums.OtpPurpose.RESET_PASSWORD, clientIp);
    }

    @Transactional
    public PasswordResetVerificationResponse verifyPasswordReset(PasswordResetVerifyRequest request, String clientIp) {
        User user = otpService.verify(
                request.usernameOrEmail(), com.estatecrm.user_service.enums.OtpPurpose.RESET_PASSWORD, request.code(), clientIp);
        return PasswordResetVerificationResponse.from(user, issuePasswordResetToken(user));
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(request.resetToken());
        } catch (JwtException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid or expired password reset token");
        }
        if (!PASSWORD_RESET_PURPOSE.equals(jwt.getClaimAsString("purpose"))) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid or expired password reset token");
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid or expired password reset token");
        }
        User user = findById(userId);
        if (user.getStatus() != UserStatus.ACTIVE
                || !sha256(user.getPassword()).equals(jwt.getClaimAsString("password_fingerprint"))) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid or expired password reset token");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedBy(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now());
    }

    @Transactional
    public void verifyOtpEmail(VerifyOtpRequest request, String clientIp) {
        User user = otpService.verify(request.usernameOrEmail(), request.purpose(), request.code(), clientIp);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setUpdatedBy(user);
    }

    private void requireSecondFactor(User user, String totpCode, String accountKey, String ipKey) {
        if (!user.isTwoFactorEnabled()) {
            return;
        }
        if (totpCode == null || !totpService.verify(user.getTwoFactorSecret(), totpCode)) {
            loginAttemptService.recordFailure(accountKey);
            loginAttemptService.recordFailure(ipKey);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid TOTP code");
        }
    }

    private void requirePassword(User user, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "Current password is incorrect");
        }
    }

    private User findUser(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier.toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));
    }

    private User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private String issuePasswordResetToken(User user) {
        Instant now = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuedAt(now)
                        .expiresAt(now.plus(PASSWORD_RESET_TOKEN_TTL))
                        .subject(user.getId().toString())
                        .claim("token_type", "password_reset")
                        .claim("purpose", PASSWORD_RESET_PURPOSE)
                        .claim("password_fingerprint", sha256(user.getPassword()))
                        .build())).getTokenValue();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
