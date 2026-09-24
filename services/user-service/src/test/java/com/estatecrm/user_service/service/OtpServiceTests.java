package com.estatecrm.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.estatecrm.user_service.entity.OtpVerification;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.OtpPurpose;
import com.estatecrm.user_service.repository.OtpVerificationRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class OtpServiceTests {

    private static final String USERNAME = "sales01";
    private static final String EMAIL = "sales01@estate.local";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final OtpVerificationRepository otpRepository = mock(OtpVerificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MailClient mailClient = mock(MailClient.class);
    private final LoginAttemptService loginAttemptService = new LoginAttemptService();
    private final OtpService otpService = new OtpService(
            otpRepository, userRepository, mailClient, loginAttemptService, TTL, 5);

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        return user;
    }

    @Test
    void sendStoresOnlyHashAndMailsSixDigitCodeToOwner() {
        User user = activeUser();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(call -> call.getArgument(0));

        otpService.send(USERNAME, OtpPurpose.LOGIN, "10.0.0.1");

        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpRepository).invalidateUnused(user.getId(), OtpPurpose.LOGIN);
        verify(otpRepository).save(captor.capture());
        OtpVerification stored = captor.getValue();
        assertThat(stored.getOtpCode()).matches("[0-9a-f]{64}");
        assertThat(stored.getPurpose()).isEqualTo(OtpPurpose.LOGIN);
        assertThat(stored.getAttempts()).isZero();
        assertThat(stored.isUsed()).isFalse();
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(4));
        verify(mailClient).send(eq(EMAIL), anyString(), org.mockito.ArgumentMatchers.matches(".*[0-9]{6}.*"));
    }

    @Test
    void sendStaysSilentForUnknownOrMaillessAccount() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());
        User mailless = activeUser();
        mailless.setEmail(null);
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.of(mailless));

        assertThatCode(() -> otpService.send("ghost", OtpPurpose.RESET_PASSWORD, "10.0.0.1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> otpService.send("nobody", OtpPurpose.RESET_PASSWORD, "10.0.0.1"))
                .doesNotThrowAnyException();

        verifyNoInteractions(mailClient);
        verify(otpRepository, never()).save(any(OtpVerification.class));
    }

    @Test
    void verifyRejectsWrongCodeWithoutConsumingOtp() {
        User user = activeUser();
        OtpVerification otp = otp(user, "123456");
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(otpRepository
                .findFirstByUserIdAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(user.getId()), eq(OtpPurpose.LOGIN), any(LocalDateTime.class)))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify(USERNAME, OtpPurpose.LOGIN, "000000", "10.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired OTP");
        assertThat(otp.isUsed()).isFalse();
        assertThat(otp.getAttempts()).isEqualTo(1);
    }

    @Test
    void verifyConsumesOtpOnce() {
        User user = activeUser();
        OtpVerification otp = otp(user, "123456");
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(otpRepository
                .findFirstByUserIdAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(user.getId()), eq(OtpPurpose.LOGIN), any(LocalDateTime.class)))
                .thenReturn(Optional.of(otp));

        assertThat(otpService.verify(USERNAME, OtpPurpose.LOGIN, "123456", "10.0.0.1").getId())
                .isEqualTo(user.getId());
        assertThat(otp.isUsed()).isTrue();
    }

    @Test
    void otpSendRateLimitDoesNotBlockVerificationBucket() {
        User user = activeUser();
        OtpVerification otp = otp(user, "123456");
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(call -> call.getArgument(0));
        when(otpRepository
                .findFirstByUserIdAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(user.getId()), eq(OtpPurpose.LOGIN), any(LocalDateTime.class)))
                .thenReturn(Optional.of(otp));

        otpService.send(USERNAME, OtpPurpose.LOGIN, "10.0.0.4");
        assertThat(otpService.verify(USERNAME, OtpPurpose.LOGIN, "123456", "10.0.0.4").getId())
                .isEqualTo(user.getId());
    }

    @Test
    void verifyMarksOtpUsedAfterFiveWrongAttempts() {
        User user = activeUser();
        OtpVerification otp = otp(user, "123456");
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(otpRepository
                .findFirstByUserIdAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(user.getId()), eq(OtpPurpose.LOGIN), any(LocalDateTime.class)))
                .thenReturn(Optional.of(otp));

        for (int index = 0; index < 5; index++) {
            assertThatThrownBy(() -> otpService.verify(USERNAME, OtpPurpose.LOGIN, "000000", "10.0.0.2"))
                    .isInstanceOf(ResponseStatusException.class);
        }

        assertThat(otp.getAttempts()).isEqualTo(5);
        assertThat(otp.isUsed()).isTrue();
    }

    @Test
    void verifyEmailPurposeIsSupportedByNewSchema() {
        User user = activeUser();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(call -> call.getArgument(0));

        assertThatCode(() -> otpService.send(USERNAME, OtpPurpose.VERIFY_EMAIL, "10.0.0.3"))
                .doesNotThrowAnyException();
        verify(mailClient).send(eq(EMAIL), org.mockito.ArgumentMatchers.contains("xac thuc"), anyString());
    }

    private OtpVerification otp(User user, String code) {
        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setOtpCode(sha256(code));
        otp.setPurpose(OtpPurpose.LOGIN);
        otp.setExpiresAt(LocalDateTime.now().plus(TTL));
        return otp;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
