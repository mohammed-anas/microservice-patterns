package com.ecommerce.orderservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Author: Mohammed Anas Date: 09/11/2026 */
@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        // Eureka uses this builder. It must not go through the load balancer or the short
        // downstream timeouts, or registration looks like a failed service call.
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // A replica that is gone, or still in Eureka for a few seconds, should fail quickly
        // enough for retry to try another instance.
        factory.setConnectTimeout(Duration.ofMillis(500));
        // Must stay above slowCallDurationThreshold (2s). A call that returns in 2.5s is a
        // slow success. A call that is still open at 3s is a failure retry can see.
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(factory);
    }
}
