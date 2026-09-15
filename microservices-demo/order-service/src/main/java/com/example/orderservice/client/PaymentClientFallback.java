package com.example.orderservice.client;

import com.example.orderservice.model.PaymentRequest;
import com.example.orderservice.model.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Failover handler for PaymentClient. Triggered by Feign's Resilience4j
 * circuit breaker (feign.circuitbreaker.enabled=true) once payment-service
 * is judged unhealthy - i.e. after retries at the OrderService layer have
 * already been exhausted for a given call, or while the breaker is
 * deliberately open to stop hammering a known-down dependency.
 *
 * In production this would typically write to an outbox/dead-letter queue
 * for later reconciliation rather than just returning a status string.
 */
@Component
public class PaymentClientFallback implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClientFallback.class);

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        log.warn("payment-service unavailable - failing over for order {}", request.getOrderId());
        return new PaymentResponse(
                request.getOrderId(),
                "FAILOVER_QUEUED",
                "payment-service is down; payment queued for reconciliation"
        );
    }
}
