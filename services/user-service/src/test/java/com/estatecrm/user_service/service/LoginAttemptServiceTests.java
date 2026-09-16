package com.estatecrm.user_service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTests {

    private final LoginAttemptService service = new LoginAttemptService();

    @Test
    void blocksAfterThresholdAndRecoversOnReset() {
        String key = "account:admin";
        for (int i = 0; i < 5; i++) {
            service.recordFailure(key);
        }

        assertThatThrownBy(() -> service.checkBlocked(key, 5))
                .isInstanceOf(RuntimeException.class);

        service.reset(key);

        assertThatCode(() -> service.checkBlocked(key, 5)).doesNotThrowAnyException();
    }

    @Test
    void separateKeysDoNotShareCounters() {
        service.recordFailure("ip:10.0.0.1");
        service.recordFailure("ip:10.0.0.1");

        assertThatCode(() -> service.checkBlocked("ip:10.0.0.2", 5)).doesNotThrowAnyException();
    }
}
