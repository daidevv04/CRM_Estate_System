package com.estatecrm.user_service.service;

import com.estatecrm.user_service.dto.auth.LoginRequest;
import com.estatecrm.user_service.dto.auth.RefreshTokenRequest;
import com.estatecrm.user_service.dto.auth.TokenResponse;
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

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final LoginAttemptService loginAttemptService;
    private final String dummyHash;
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
            @Value("${app.auth.dummy-bcrypt-hash}") String dummyHash,
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
        this.dummyHash = decodedDummy;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.maxLoginAttempts = maxLoginAttempts;
        this.maxLoginAttemptsIp = maxLoginAttemptsIp;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp) {
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
        // Xoa ca bucket IP: neu khong, vai lan go nham cua nguoi khac trong cung
        // 15 phut se khoa ca van phong du nguoi sau go dung mat khau.
        loginAttemptService.reset(accountKey);
        loginAttemptService.reset(ipKey);
        return issueTokens(user, Instant.now(), LocalDateTime.now());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse refresh(RefreshTokenRequest request) {
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

        // Atomic single-use claim. Failure means the token raced, was already
        // rotated, or expired. Only an already-revoked token signals reuse.
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
        return issueTokens(user, now, databaseNow);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.tryRevoke(sha256(request.refreshToken()), LocalDateTime.now());
    }

    private TokenResponse issueTokens(User user, Instant now, LocalDateTime databaseNow) {
        Instant accessExpiresAt = now.plus(accessTokenTtl);
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("user-service")
                        .issuedAt(now)
                        .expiresAt(accessExpiresAt)
                        .subject(user.getId().toString())
                        .claim("username", user.getUsername())
                        .claim("role", user.getRole().name())
                        .build())).getTokenValue();

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
        refreshTokenRepository.save(storedToken);

        return new TokenResponse(
                accessToken, refreshToken, "Bearer", accessTokenTtl.toSeconds());
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