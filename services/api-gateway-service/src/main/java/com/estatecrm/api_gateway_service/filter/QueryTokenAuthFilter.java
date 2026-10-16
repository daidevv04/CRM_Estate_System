package com.estatecrm.api_gateway_service.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Ho tro client nhu browser EventSource (SSE) khong the gui custom header Authorization.
 * Doc JWT tu query param "token" hoac "access_token" va gan vao header Authorization: Bearer <token>
 * truoc khi chay qua cac bo loc bao mat (Security WebFilterChain) va chuyen tiep downstream.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class QueryTokenAuthFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            String token = request.getQueryParams().getFirst("token");
            if (token == null || token.isBlank()) {
                token = request.getQueryParams().getFirst("access_token");
            }

            if (token != null && !token.isBlank()) {
                final String bearer = "Bearer " + token.trim();
                ServerWebExchange mutated = exchange.mutate()
                        .request(builder -> builder.header(HttpHeaders.AUTHORIZATION, bearer))
                        .build();
                return chain.filter(mutated);
            }
        }

        return chain.filter(exchange);
    }
}
