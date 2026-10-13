package com.estatecrm.user_service.dto.auth;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserSessionResponse(
        UUID id,
        String deviceName,
        String ipAddress,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        boolean current) {
}
