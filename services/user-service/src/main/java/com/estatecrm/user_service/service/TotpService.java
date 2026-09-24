package com.estatecrm.user_service.service;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * TOTP theo RFC 6238 cho 2FA. Viet bang JDK (HmacSHA1 + Base32 tu lam) thay vi
 * them mot thu vien chi de dung ~80 dong nay.
 *
 * Cua so +/-1 time step de bu lech dong ho giua dien thoai va server.
 */
@Component
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int DIGITS = 6;
    private static final long TIME_STEP_SECONDS = 30;
    /** 160 bit: do dai khoa muc khuyen nghi cua RFC 4226. */
    private static final int SECRET_BYTES = 20;

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public boolean verify(String secret, String code) {
        return verify(secret, code, Instant.now());
    }

    public boolean verify(String secret, String code, Instant now) {
        if (secret == null || code == null || code.length() != DIGITS) {
            return false;
        }
        long counter = now.getEpochSecond() / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (constantTimeEquals(code, generateCode(secret, counter + offset))) {
                return true;
            }
        }
        return false;
    }

    /** HOTP cua RFC 4226: HMAC-SHA1 roi cat dong (dynamic truncation) ve 6 chu so. */
    public String generateCode(String secret, long counter) {
        byte[] key = base32Decode(secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA1 is unavailable", exception);
        }
    }

    /** URI de app authenticator quet QR. */
    public String otpauthUri(String issuer, String account, String secret) {
        return "otpauth://totp/" + urlEncode(issuer + ":" + account)
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&digits=" + DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** So sanh khong phu thuoc thoi gian de khong lo vi tri chu so dung. */
    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int index = 0; index < left.length(); index++) {
            diff |= left.charAt(index) ^ right.charAt(index);
        }
        return diff == 0;
    }

    private String base32Encode(byte[] bytes) {
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                encoded.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            encoded.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return encoded.toString();
    }

    private byte[] base32Decode(String secret) {
        String normalized = secret.trim().replace("=", "").toUpperCase(java.util.Locale.ROOT);
        java.io.ByteArrayOutputStream decoded = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int index = 0; index < normalized.length(); index++) {
            int value = BASE32_ALPHABET.indexOf(normalized.charAt(index));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                decoded.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return decoded.toByteArray();
    }
}
