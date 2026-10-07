package com.estatecrm.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.user_service.dto.auth.PasswordResetConfirmRequest;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.repository.RefreshTokenRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTests {

    @Test
    void confirmChangesPasswordAndRevokesEveryExistingSession() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtDecoder decoder = mock(JwtDecoder.class);
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setPassword("old-password-hash");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(passwords.encode("NewPassword1!")).thenReturn("new-password-hash");
        when(decoder.decode("reset-token")).thenReturn(Jwt.withTokenValue("reset-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("purpose", "password-reset")
                .claim("password_fingerprint", sha256(user.getPassword()))
                .build());

        authService(users, refreshTokens, passwords, decoder)
                .confirmPasswordReset(new PasswordResetConfirmRequest("reset-token", "NewPassword1!"));

        assertThat(user.getPassword()).isEqualTo("new-password-hash");
        verify(refreshTokens).revokeAllForUser(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any());
    }

    private AuthService authService(
            UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwords, JwtDecoder decoder) {
        return new AuthService(
                users, refreshTokens, passwords, mock(JwtEncoder.class), decoder,
                mock(LoginAttemptService.class), mock(OtpService.class), mock(TotpService.class),
                Base64.getEncoder().encodeToString("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuuabcdefghijkl".getBytes()),
                "EstateCRM", Duration.ofMinutes(15), Duration.ofDays(30), 5, 50);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}