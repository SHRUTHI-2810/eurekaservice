package com.example.orderservice.client;

import com.example.orderservice.model.PaymentRequest;
import com.example.orderservice.model.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP client for payment-service. The "name" is the service's
 * Eureka registration id - Feign + Ribbon-style client-side load balancing
 * resolves it to a live instance, so there's no hardcoded host:port.
 *
 * fallback = PaymentClientFallback wires in a failover mechanism: when
 * payment-service is unreachable or its circuit breaker is open, calls are
 * routed to the fallback instead of throwing, so order-service degrades
 * gracefully instead of failing the whole request chain.
 */
@FeignClient(name = "payment-service", fallback = PaymentClientFallback.class)
public interface PaymentClient {

    @PostMapping("/api/payments")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);
}
