package com.estatecrm.api_gateway_service.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class QueryTokenAuthFilterTests {

    private final QueryTokenAuthFilter filter = new QueryTokenAuthFilter();

    @Test
    void extractsTokenQueryParamAndSetsAuthorizationHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notifications/stream?token=my-jwt-token"));
        AtomicReference<String> downstreamAuth = new AtomicReference<>();

        filter.filter(exchange, mutated -> {
            downstreamAuth.set(mutated.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        }).block();

        assertThat(downstreamAuth.get()).isEqualTo("Bearer my-jwt-token");
    }

    @Test
    void extractsAccessTokenQueryParamWhenTokenParamAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notifications/stream?access_token=fallback-token"));
        AtomicReference<String> downstreamAuth = new AtomicReference<>();

        filter.filter(exchange, mutated -> {
            downstreamAuth.set(mutated.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        }).block();

        assertThat(downstreamAuth.get()).isEqualTo("Bearer fallback-token");
    }

    @Test
    void preservesExistingAuthorizationHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notifications/stream?token=new-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer existing-token"));
        AtomicReference<String> downstreamAuth = new AtomicReference<>();

        filter.filter(exchange, mutated -> {
            downstreamAuth.set(mutated.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        }).block();

        assertThat(downstreamAuth.get()).isEqualTo("Bearer existing-token");
    }

    @Test
    void leavesAuthorizationHeaderEmptyWhenNoParam() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notifications/stream"));
        AtomicReference<String> downstreamAuth = new AtomicReference<>();

        filter.filter(exchange, mutated -> {
            downstreamAuth.set(mutated.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        }).block();

        assertThat(downstreamAuth.get()).isNull();
    }
}
