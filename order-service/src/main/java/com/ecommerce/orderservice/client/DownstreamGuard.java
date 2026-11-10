package com.ecommerce.orderservice.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Applies retry and a circuit breaker to one downstream call.
 *
 * <p>Order is circuit breaker, then retry, then the HTTP call. Retry is inside the breaker, so
 * the three attempts count as a single success or a single failure. When the breaker is open it
 * rejects the call before any retry, which stops a dead dependency from being hit three more times.
 *
 * <p>The fallback sits outside both. If it ran inside the breaker, the fallback result would look
 * like a success and the breaker would never open.
 *
 * <p>These calls are safe to retry because they are reads. A request that creates a charge or an
 * order would need an idempotency key before the same retry policy could wrap it.
 *
 * <p>Author: Mohammed Anas Date: 10/11/2026
 */
@Component
public class DownstreamGuard {

    static final String PAYMENT = "payment-service";
    static final String INVENTORY = "inventory-service";

    private static final Logger log = LoggerFactory.getLogger(DownstreamGuard.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public DownstreamGuard(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        registerBreakers();
    }

    private void registerBreakers() {
        // Create them at startup so actuator shows CLOSED before the first order, and so the
        // state-transition logger is attached before any call.
        circuitBreakerRegistry.circuitBreaker(PAYMENT);
        circuitBreakerRegistry.circuitBreaker(INVENTORY);
        retryRegistry.retry(PAYMENT);
        retryRegistry.retry(INVENTORY);
    }

    public Map<String, String> execute(String serviceName, Supplier<Map<String, String>> remoteCall) {
        Retry retry = retryRegistry.retry(serviceName);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
        Supplier<Map<String, String>> retrying = Retry.decorateSupplier(retry, remoteCall);
        Supplier<Map<String, String>> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, retrying);
        try {
            return guarded.get();
        } catch (HttpClientErrorException clientError) {
            // Not retried and not recorded. The dependency answered; the request was wrong.
            return Map.of(
                    "service", serviceName,
                    "container", "unavailable",
                    "ip", "unavailable",
                    "port", "8080",
                    "message", "client_error",
                    "error", clientError.getStatusCode().toString());
        } catch (CallNotPermittedException open) {
            log.info("circuit open, skipping call service={}", serviceName);
            return unavailable(serviceName, open, "circuit_open");
        } catch (ResourceAccessException | HttpServerErrorException failure) {
            log.warn("downstream failed after retry service={} error={}", serviceName, failure.toString());
            return unavailable(serviceName, failure, "dependency_failed");
        } catch (RuntimeException failure) {
            // Includes "no instances available" from the load balancer. One failure, no retry:
            // retryExceptions does not list this error, so it is not attempted three times.
            log.warn("downstream failed service={} error={}", serviceName, failure.toString());
            return unavailable(serviceName, failure, "dependency_failed");
        }
    }

    private static Map<String, String> unavailable(String serviceName, RuntimeException failure, String message) {
        return Map.of(
                "service", serviceName,
                "container", "unavailable",
                "ip", "unavailable",
                "port", "8080",
                "message", message,
                "error", failure.getClass().getSimpleName());
    }
}
