package com.example.paymentservice.controller;

import com.example.paymentservice.model.PaymentRequest;
import com.example.paymentservice.model.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Downstream "payment" service. order-service calls this over Feign,
 * looking it up by logical name (payment-service) through Eureka.
 *
 * app.simulate-failures (pulled from Config Server, live-editable) lets you
 * flip this endpoint into "always fail" mode on demand, without touching
 * code, to demonstrate order-service's retry + failover behavior.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Value("${app.simulate-failures:false}")
    private boolean simulateFailures;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        if (simulateFailures) {
            // Simulates an outage/transient error so callers can exercise
            // their retry + circuit-breaker/fallback paths.
            throw new IllegalStateException("payment-service is deliberately failing (app.simulate-failures=true)");
        }

        PaymentResponse response = new PaymentResponse(
                request.getOrderId(),
                "SUCCESS",
                "Processed payment of $" + request.getAmount() + " for order " + request.getOrderId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public String health() {
        return "payment-service is up";
    }
}
