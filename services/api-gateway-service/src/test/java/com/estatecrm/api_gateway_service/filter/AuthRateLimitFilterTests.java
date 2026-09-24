package com.estatecrm.api_gateway_service.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class AuthRateLimitFilterTests {

    @Test
    void blocksThirdAuthRequestFromSameIpAndSetsRetryAfter() {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                2, Duration.ofMinutes(1), Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));
        AtomicInteger forwarded = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };

        MockServerWebExchange first = exchange("/api/auth/login");
        MockServerWebExchange second = exchange("/api/auth/login");
        MockServerWebExchange third = exchange("/api/auth/login");
        filter.filter(first, chain).block();
        filter.filter(second, chain).block();
        filter.filter(third, chain).block();

        assertThat(forwarded).hasValue(2);
        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    @Test
    void doesNotLimitProtectedBusinessRequests() {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                1, Duration.ofMinutes(1), Clock.systemUTC());
        AtomicInteger forwarded = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };

        filter.filter(exchange("/api/customers"), chain).block();
        filter.filter(exchange("/api/customers"), chain).block();

        assertThat(forwarded).hasValue(2);
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345)));
    }
}
