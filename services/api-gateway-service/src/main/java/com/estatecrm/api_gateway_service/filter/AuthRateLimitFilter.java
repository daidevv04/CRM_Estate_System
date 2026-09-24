package com.estatecrm.api_gateway_service.filter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Hang rao som cho auth endpoint. Key dung remote IP gateway thay vi
 * X-Forwarded-For: gateway hien khong co trusted reverse-proxy allow-list, tin
 * header nay se cho attacker tu gia IP de bypass limit.
 *
 * ponytail: limiter chi in-memory, phu hop mot gateway instance. Chuyen sang
 * RedisRateLimiter truoc khi scale gateway qua mot replica.
 */
@Component
public class AuthRateLimitFilter implements WebFilter {

    private static final Set<String> AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/otp/send", "/api/auth/otp/verify", "/api/auth/2fa/verify");
    private static final int MAX_BUCKETS = 4_096;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private int maxRequests;
    private Duration window;
    private Clock clock;

    /** Spring 7 component scan can khoi tao default constructor cho WebFilter. */
    public AuthRateLimitFilter() {
        this(20, Duration.ofMinutes(1), Clock.systemUTC());
    }

    @Value("${app.gateway.auth-rate-limit.max-requests:20}")
    void setMaxRequests(int maxRequests) {
        validate(maxRequests, window);
        this.maxRequests = maxRequests;
    }

    @Value("${app.gateway.auth-rate-limit.window:1m}")
    void setWindow(Duration window) {
        validate(maxRequests, window);
        this.window = window;
    }

    AuthRateLimitFilter(int maxRequests, Duration window, Clock clock) {
        validate(maxRequests, window);
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    private void validate(int maxRequests, Duration window) {
        if (maxRequests < 1 || window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Rate limit maxRequests and window must be positive");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST
                || !AUTH_PATHS.contains(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        Instant now = clock.instant();
        Counter counter = counters.computeIfAbsent(clientIp(exchange), ignored -> new Counter(now));
        synchronized (counter) {
            if (!now.isBefore(counter.windowStartedAt.plus(window))) {
                counter.windowStartedAt = now;
                counter.requests = 0;
            }
            if (counter.requests >= maxRequests) {
                long retryAfter = Math.max(1, Duration.between(now, counter.windowStartedAt.plus(window)).toSeconds());
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
                return exchange.getResponse().setComplete();
            }
            counter.requests++;
        }
        pruneIfNeeded(now);
        return chain.filter(exchange);
    }

    private String clientIp(ServerWebExchange exchange) {
        var address = exchange.getRequest().getRemoteAddress();
        return address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress();
    }

    private void pruneIfNeeded(Instant now) {
        if (counters.size() > MAX_BUCKETS) {
            counters.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().windowStartedAt.plus(window)));
        }
    }

    private static final class Counter {
        private Instant windowStartedAt;
        private int requests;

        private Counter(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
