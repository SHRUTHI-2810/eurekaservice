package com.example.orderservice.service;

import com.example.orderservice.client.PaymentClient;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.PaymentRequest;
import com.example.orderservice.model.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business/service layer, called by OrderController.
 *
 * Retry Pattern: placeOrder() retries the downstream payment call up to
 * 3 times with exponential backoff (500ms, 1000ms, 2000ms) to absorb
 * transient failures (a blip, a brief network hiccup) without surfacing
 * an error to the caller.
 *
 * Failover: if all retries are exhausted, @Recover runs instead of
 * propagating the exception - and independently, PaymentClientFallback
 * (registered on the Feign client) takes over if payment-service's
 * circuit breaker is open. Either path keeps order-service responsive
 * instead of cascading the downstream failure.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // In-memory store for demo purposes; swap for a real repository/DB.
    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    private final PaymentClient paymentClient;

    public OrderService(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public Order placeOrder(Order order) {
        order.setId(UUID.randomUUID().toString());
        log.info("Attempting payment for order {}", order.getId());

        PaymentRequest paymentRequest = new PaymentRequest(order.getId(), order.getAmount());
        PaymentResponse paymentResponse = paymentClient.processPayment(paymentRequest);

        order.setPaymentStatus(paymentResponse.getStatus());
        orderStore.put(order.getId(), order);
        return order;
    }

    /**
     * Invoked automatically by Spring Retry once all @Retryable attempts
     * for placeOrder(...) have failed. Signature must match the retried
     * method's args, prefixed with the exception type.
     */
    @Recover
    public Order recoverFromPaymentFailure(RuntimeException ex, Order order) {
        log.error("All retry attempts exhausted for order, failing over: {}", ex.getMessage());
        order.setId(UUID.randomUUID().toString());
        order.setPaymentStatus("PAYMENT_UNAVAILABLE_FAILOVER_APPLIED");
        orderStore.put(order.getId(), order);
        return order;
    }

    public Order getOrder(String id) {
        Order order = orderStore.get(id);
        if (order == null) {
            throw new NoSuchElementException("No order found with id " + id);
        }
        return order;
    }
}
