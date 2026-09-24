package com.estatecrm.api_gateway_service.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class RequestIdFilterTests {

    @Test
    void replacesClientSuppliedIdAndForwardsSameGeneratedId() {
        RequestIdFilter filter = new RequestIdFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/customers")
                .header(RequestIdFilter.HEADER, "untrusted-client-value"));
        AtomicReference<String> downstreamId = new AtomicReference<>();

        filter.filter(exchange, downstream -> {
            downstreamId.set(downstream.getRequest().getHeaders().getFirst(RequestIdFilter.HEADER));
            return Mono.empty();
        }).block();

        String responseId = exchange.getResponse().getHeaders().getFirst(RequestIdFilter.HEADER);
        assertThat(responseId).isNotBlank().isNotEqualTo("untrusted-client-value");
        assertThat(downstreamId.get()).isEqualTo(responseId);
    }
}
