package com.estatecrm.user_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estatecrm.user_service.config.SecurityConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Kiem tra refresh token khong dung duoc nhu bearer token.
 *
 * Refresh token duoc ky bang cung JWT_SECRET nen chu ky hop le; thu phan biet
 * duy nhat la claim "token_type". Neu converter khong chan, TTL access token 15
 * phut bi vo hieu vi refresh token song 30 ngay.
 *
 * Test nay khong can DB hay Consul nen chay ca khi IT_INFRA khong bat.
 */
class JwtAuthenticationConverterTests {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void refreshTokenIsRejected() {
        Jwt refreshToken = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .claims(claims -> claims.putAll(Map.of("sub", "u1", "token_type", "refresh")))
                .build();

        assertThatThrownBy(() -> securityConfig.jwtAuthenticationConverter().convert(refreshToken))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Refresh token");
    }

    @Test
    void accessTokenIsAcceptedWithRoleAuthority() {
        Jwt accessToken = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .claims(claims -> claims.putAll(Map.of("sub", "u1", "role", "SALES")))
                .build();

        assertThat(securityConfig.jwtAuthenticationConverter().convert(accessToken))
                .isNotNull()
                .extracting(token -> token.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_SALES");
    }
}
