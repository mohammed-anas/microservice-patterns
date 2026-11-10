package com.ecommerce.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Author: Mohammed Anas Date: 10/11/2026 */
@Configuration
public class ResilienceEventConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceEventConfig.class);

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEvents() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                event.getAddedEntry().getEventPublisher()
                        .onStateTransition(transition -> log.info(
                                "circuit-breaker name={} transition={}",
                                transition.getCircuitBreakerName(),
                                transition.getStateTransition()))
                        .onFailureRateExceeded(exceeded -> log.info(
                                "circuit-breaker name={} failureRate={}%",
                                exceeded.getCircuitBreakerName(),
                                exceeded.getFailureRate()))
                        .onSlowCallRateExceeded(exceeded -> log.info(
                                "circuit-breaker name={} slowCallRate={}%",
                                exceeded.getCircuitBreakerName(),
                                exceeded.getSlowCallRate()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
            }
        };
    }

    @Bean
    public RegistryEventConsumer<Retry> retryEvents() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> event) {
                event.getAddedEntry().getEventPublisher()
                        .onRetry(retry -> log.info(
                                "retry name={} attempt={} error={}",
                                retry.getName(),
                                retry.getNumberOfRetryAttempts(),
                                retry.getLastThrowable().getClass().getSimpleName()))
                        .onError(retry -> log.info(
                                "retry exhausted name={} attempts={} error={}",
                                retry.getName(),
                                retry.getNumberOfRetryAttempts(),
                                retry.getLastThrowable().getClass().getSimpleName()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> event) {
            }
        };
    }
}
