package com.estatecrm.user_service.service;

import com.estatecrm.user_service.dto.mail.SendMailRequest;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Goi mail-service qua REST. Khong forward token cua nhan vien: user-service tu
 * ky mot internal token song 1 phut (claim scope=internal) chi de goi mail.
 */
@Component
public class MailClient {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final JwtEncoder jwtEncoder;

    public MailClient(@Value("${app.mail.base-url}") String baseUrl, JwtEncoder jwtEncoder) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.jwtEncoder = jwtEncoder;
    }

    public void send(String to, String subject, String body) {
        try {
            restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendMailRequest(to, subject, body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Mail service unavailable");
        }
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
                        .claim("scope", "internal")
                        .build())).getTokenValue();
    }
}
