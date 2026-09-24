package com.estatecrm.user_service.service;

import com.estatecrm.user_service.entity.OtpVerification;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.OtpPurpose;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.repository.OtpVerificationRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * OTP dung mot lan gui qua email (LOGIN, RESET_PASSWORD).
 *
 * Gioi han tan suat dung lai LoginAttemptService voi key rieng "otp:...": key
 * "account:" danh cho sai mat khau nen khong the dung chung.
 *
 * Database V2 chi luu SHA-256 cua ma 6 chu so. Ma goc chi ton tai trong request
 * gui mail; ma song 5 phut, dung mot lan, va ma cu bi vo hieu khi phat hanh ma moi.
 */
@Service
public class OtpService {

    private static final int OTP_BOUND = 1_000_000;
    /** Database attempts la hang rao ben vung qua restart/scale-out. */
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final OtpVerificationRepository otpRepository;
    private final UserRepository userRepository;
    private final MailClient mailClient;
    private final LoginAttemptService loginAttemptService;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final int maxAttempts;

    public OtpService(
            OtpVerificationRepository otpRepository,
            UserRepository userRepository,
            MailClient mailClient,
            LoginAttemptService loginAttemptService,
            @Value("${app.auth.otp-ttl:5m}") Duration ttl,
            @Value("${app.auth.max-login-attempts:5}") int maxAttempts) {
        this.otpRepository = otpRepository;
        this.userRepository = userRepository;
        this.mailClient = mailClient;
        this.loginAttemptService = loginAttemptService;
        this.ttl = ttl;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Sinh va gui OTP. Luon tra ve binh thuong du tai khoan khong ton tai hay
     * khong co email, de khong bien endpoint nay thanh cong cu do tai khoan.
     */
    @Transactional
    public void send(String usernameOrEmail, OtpPurpose purpose, String clientIp) {
        // Bucket gui tach bucket verify: xin ma 5 lan thi bi throttle gui, nhung
        // khong duoc tu khoa luon OTP vua nhan cua chinh minh.
        String accountKey = sendAccountKey(usernameOrEmail);
        String ipKey = sendIpKey(clientIp);
        loginAttemptService.checkBlocked(accountKey, maxAttempts);
        loginAttemptService.checkBlocked(ipKey, maxAttempts);
        loginAttemptService.recordFailure(accountKey);
        loginAttemptService.recordFailure(ipKey);

        User user = findUser(usernameOrEmail).orElse(null);
        if (user == null || user.getEmail() == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        String code = String.format("%06d", random.nextInt(OTP_BOUND));
        otpRepository.invalidateUnused(user.getId(), purpose);
        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setOtpCode(sha256(code));
        otp.setPurpose(purpose);
        otp.setExpiresAt(LocalDateTime.now().plus(ttl));
        otpRepository.save(otp);

        // Gui trong cung transaction: mail loi thi khong de lai OTP "mo coi" ma
        // nguoi dung khong bao gio nhan duoc.
        mailClient.send(user.getEmail(), subject(purpose), body(code));
    }

    /** Doi OTP lay user. Sai/het han/khong ton tai deu tra 401 cung mot message. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public User verify(String usernameOrEmail, OtpPurpose purpose, String code, String clientIp) {
        String accountKey = accountKey(usernameOrEmail);
        String ipKey = ipKey(clientIp);
        loginAttemptService.checkBlocked(accountKey, maxAttempts);
        loginAttemptService.checkBlocked(ipKey, maxAttempts);

        User user = findUser(usernameOrEmail).orElseThrow(OtpService::invalidOtp);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(FORBIDDEN, "User is not active");
        }
        OtpVerification otp = otpRepository
                .findFirstByUserIdAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(), purpose, LocalDateTime.now())
                .orElseThrow(OtpService::invalidOtp);
        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS || !otp.getOtpCode().equals(sha256(code))) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
                otp.setUsed(true);
            }
            loginAttemptService.recordFailure(accountKey);
            loginAttemptService.recordFailure(ipKey);
            throw invalidOtp();
        }
        otp.setUsed(true);
        loginAttemptService.reset(accountKey);
        loginAttemptService.reset(ipKey);
        return user;
    }

    private Optional<User> findUser(String usernameOrEmail) {
        String identifier = usernameOrEmail.trim();
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier.toLowerCase(Locale.ROOT)));
    }


    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Bucket verify; sai ma se khoa dung bucket nay. */
    private String accountKey(String usernameOrEmail) {
        return "otp:" + usernameOrEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String ipKey(String clientIp) {
        return "ip:" + clientIp;
    }

    /** Bucket gui tach rieng de throttle spam ma khong chan buoc verify. */
    private String sendAccountKey(String usernameOrEmail) {
        return "otp-send:" + usernameOrEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String sendIpKey(String clientIp) {
        return "otp-send-ip:" + clientIp;
    }

    private String subject(OtpPurpose purpose) {
        if (purpose == OtpPurpose.RESET_PASSWORD) {
            return "Ma dat lai mat khau CRM";
        }
        if (purpose == OtpPurpose.VERIFY_EMAIL) {
            return "Ma xac thuc email CRM";
        }
        return "Ma dang nhap CRM";
    }

    private String body(String code) {
        return "Ma cua ban la " + code + ". Ma het hieu luc sau "
                + ttl.toMinutes() + " phut. Khong chia se ma nay voi bat ky ai.";
    }

    private static ResponseStatusException invalidOtp() {
        return new ResponseStatusException(UNAUTHORIZED, "Invalid or expired OTP");
    }
}
