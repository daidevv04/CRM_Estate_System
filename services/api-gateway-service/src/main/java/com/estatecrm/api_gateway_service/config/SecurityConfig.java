package com.estatecrm.api_gateway_service.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Verify access token tai cua ngo vao. Cac service phia sau van verify lai
 * (defense in depth) nen goi thang vao port noi bo khong bypass duoc auth.
 *
 * Dung ban Reactive vi Spring Cloud Gateway chay tren WebFlux, khong dung
 * duoc SecurityFilterChain cua servlet.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        byte[] key = Base64.getDecoder().decode(secret);
        if (key.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusReactiveJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // /error phai public, neu khong moi loi chua xac thuc bi doi
                        // thanh 401 rong thay vi 404/400 that su.
                        .pathMatchers("/actuator/health", "/error").permitAll()
                        // Login, refresh va cac buoc xac thuc chua co token (OTP, 2FA)
                        // phai vao duoc khi chua co token.
                        .pathMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/refresh",
                                // Logout nhan refresh token trong request body; token nay bi chan lam
                                // bearer token, nen phai public de user-service revoke duoc token.
                                "/api/auth/logout",
                                "/api/auth/otp/send",
                                "/api/auth/otp/verify",
                                "/api/auth/2fa/verify")
                        .permitAll()
                        // CORS preflight khong mang Authorization header.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }
}
