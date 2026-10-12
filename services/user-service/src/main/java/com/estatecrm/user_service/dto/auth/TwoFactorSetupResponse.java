package com.estatecrm.user_service.dto.auth;

public record TwoFactorSetupResponse(
        String secret,
        String otpauthUri) {
}
