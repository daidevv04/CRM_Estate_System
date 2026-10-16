package com.estatecrm.user_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CrmClient {

    private static final Logger log = LoggerFactory.getLogger(CrmClient.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(2);

    private final RestClient restClient;
    private final JwtEncoder jwtEncoder;

    public CrmClient(
            @Value("${app.crm.base-url:http://localhost:8083}") String baseUrl,
            JwtEncoder jwtEncoder) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.jwtEncoder = jwtEncoder;
    }

    public List<UUID> getManagedUserIds(UUID managerId) {
        if (managerId == null) {
            return List.of();
        }
        try {
            List<UUID> result = restClient.get()
                    .uri("/projects/internal/managed-user-ids/{managerId}", managerId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UUID>>() {});
            if (result != null && !result.isEmpty()) {
                return result;
            }
        } catch (Exception ex) {
            log.warn("Không thể lấy danh sách nhân sự phụ trách từ crm-service cho manager {}: {}. Sử dụng fallback an toàn.",
                    managerId, ex.getMessage());
        }
        return List.of(managerId);
    }

    private String internalToken() {
        Instant now = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("user-service")
                        .issuedAt(now)
                        .expiresAt(now.plus(TOKEN_TTL))
                        .subject("user-service")
                        .claim("role", "ADMIN")
                        .build())).getTokenValue();
    }
}
