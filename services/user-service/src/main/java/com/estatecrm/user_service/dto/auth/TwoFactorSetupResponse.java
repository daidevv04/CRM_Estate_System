package com.estatecrm.user_service.dto.auth;

/** secret chi hien mot lan; otpauthUri de client ve QR cho app authenticator. */
public record TwoFactorSetupResponse(
        String secret,
        String otpauthUri) {
}
