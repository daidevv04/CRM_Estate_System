package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.dto.mail.SendMailRequest;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Component
public class MailClient {

    private static final Logger log = LoggerFactory.getLogger(MailClient.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final JwtEncoder jwtEncoder;

    public MailClient(@Value("${app.mail.base-url}") String baseUrl, JwtEncoder jwtEncoder) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.jwtEncoder = jwtEncoder;
    }

    public void send(String to, String subject, String body) {
        send(to, subject, body, null, null, null);
    }

    public void send(String to, String subject, String body, String html, String replyTo, String senderName) {
        try {
            restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendMailRequest(to, subject, body, html, replyTo, senderName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Gọi mail-service thất bại: {}", exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Dịch vụ gửi mail không phản hồi: " + exception.getMessage());
        }
    }

    private String internalToken() {
        Instant now = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("customer-service")
                        .issuedAt(now)
                        .expiresAt(now.plus(TOKEN_TTL))
                        .subject("customer-service")
                        .claim("scope", "internal")
                        .build())).getTokenValue();
    }
}
