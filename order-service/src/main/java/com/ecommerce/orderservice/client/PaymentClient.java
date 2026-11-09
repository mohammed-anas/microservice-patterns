package com.ecommerce.orderservice.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Author: Mohammed Anas Date: 09/11/2026 */
@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public Map<String, String> callPayment() {
        return restClient.get()
                .uri("http://payment-service/payments")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
