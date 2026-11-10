package com.ecommerce.orderservice.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/** Author: Mohammed Anas Date: 09/11/2026 */
@Component
public class PaymentClient {

    private final RestClient restClient;
    private final DownstreamGuard guard;

    public PaymentClient(@LoadBalanced RestClient.Builder builder, DownstreamGuard guard) {
        this.restClient = builder.build();
        this.guard = guard;
    }

    public Map<String, String> callPayment() {
        return callPayment(0, false, false);
    }

    public Map<String, String> callPayment(long delayMs, boolean fail, boolean clientError) {
        return guard.execute(DownstreamGuard.PAYMENT, () -> restClient.get()
                .uri(uri(delayMs, fail, clientError))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}));
    }

    private static URI uri(long delayMs, boolean fail, boolean clientError) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("http://payment-service/payments");
        // Omit the defaults so a normal order still calls /payments with no query string.
        if (delayMs > 0) {
            builder.queryParam("delayMs", delayMs);
        }
        if (fail) {
            builder.queryParam("fail", true);
        }
        if (clientError) {
            builder.queryParam("clientError", true);
        }
        return builder.build().toUri();
    }
}
