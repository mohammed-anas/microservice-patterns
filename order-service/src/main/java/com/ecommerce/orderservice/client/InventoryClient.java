package com.ecommerce.orderservice.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Author: Mohammed Anas Date: 09/11/2026 */
@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public Map<String, String> callInventory() {
        return restClient.get()
                .uri("http://inventory-service/inventory")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
