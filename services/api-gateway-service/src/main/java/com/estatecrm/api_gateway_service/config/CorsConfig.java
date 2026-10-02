package com.estatecrm.api_gateway_service.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import com.estatecrm.api_gateway_service.filter.RequestIdFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Browser CORS chi cho origin da khai bao. Khong dung '*' vi API mang Bearer
 * token; production phai dat CORS_ALLOWED_ORIGINS thanh domain frontend that.
 *
 * KHONG dung CorsWebFilter: no nam sau Spring Security (order -100) nen response
 * 401/403 do security tu viet se khong co header Access-Control-Allow-Origin.
 * Browser khi do bao loi CORS chu khong phai 401, frontend khong the biet ma
 * refresh token -> sau F5 (access token trong RAM da mat) app ket o trang thai
 * loading. Vi vay CORS duoc khai bao o day va bat trong ServerHttpSecurity bang
 * .cors(Customizer.withDefaults()) de moi response deu co header.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.gateway.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(java.util.List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PATCH.name(),
                HttpMethod.PUT.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(java.util.List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, RequestIdFilter.HEADER));
        configuration.setExposedHeaders(java.util.List.of(RequestIdFilter.HEADER));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
