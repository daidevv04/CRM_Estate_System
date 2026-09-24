package com.estatecrm.api_gateway_service.filter;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Tao request ID tai cua ngo, forward xuong service va tra lai client de trace log. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    public static final String HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Luon tao tai gateway: khong tin ID client gui de tranh log injection/collision.
        String requestId = UUID.randomUUID().toString();
        // Response cua exchange co the duoc mock/replace o filter chain, nen set
        // tren exchange goc truoc khi mutate request de header luon ra client.
        exchange.getResponse().getHeaders().set(HEADER, requestId);
        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, requestId)))
                .build();
        return chain.filter(mutated);
    }
}
