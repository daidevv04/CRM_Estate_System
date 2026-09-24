package com.estatecrm.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Vector lay tu RFC 4226 phu luc D (khoa ASCII "12345678901234567890", ma 6 chu
 * so). Day la lop bao ve duy nhat cho phan HMAC/Base32 viet tay.
 */
class TotpServiceTests {

    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpService totpService = new TotpService();

    @Test
    void matchesRfc4226HotpVectors() {
        assertThat(totpService.generateCode(RFC_SECRET, 0)).isEqualTo("755224");
        assertThat(totpService.generateCode(RFC_SECRET, 1)).isEqualTo("287082");
        assertThat(totpService.generateCode(RFC_SECRET, 2)).isEqualTo("359152");
        assertThat(totpService.generateCode(RFC_SECRET, 5)).isEqualTo("254676");
    }

    @Test
    void verifyAcceptsCurrentStepAndNeighbourStepsOnly() {
        Instant at59Seconds = Instant.ofEpochSecond(59);
        // counter = 1 luc t=59; cua so +/-1 gom counter 0, 1, 2.
        assertThat(totpService.verify(RFC_SECRET, "287082", at59Seconds)).isTrue();
        assertThat(totpService.verify(RFC_SECRET, "755224", at59Seconds)).isTrue();
        assertThat(totpService.verify(RFC_SECRET, "359152", at59Seconds)).isTrue();
        assertThat(totpService.verify(RFC_SECRET, "969429", at59Seconds)).isFalse();
    }

    @Test
    void verifyRejectsMalformedInput() {
        assertThat(totpService.verify(null, "287082")).isFalse();
        assertThat(totpService.verify(RFC_SECRET, "28708")).isFalse();
        assertThat(totpService.verify(RFC_SECRET, "abcdef")).isFalse();
    }

    @Test
    void generatedSecretIsBase32AndRoundTrips() {
        String secret = totpService.generateSecret();

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(totpService.verify(secret, totpService.generateCode(secret, 42), Instant.ofEpochSecond(42 * 30)))
                .isTrue();
    }

    @Test
    void otpauthUriCarriesIssuerAndPeriod() {
        assertThat(totpService.otpauthUri("EstateCRM", "sales", RFC_SECRET))
                .isEqualTo("otpauth://totp/EstateCRM%3Asales?secret=" + RFC_SECRET
                        + "&issuer=EstateCRM&digits=6&period=30");
    }
}
