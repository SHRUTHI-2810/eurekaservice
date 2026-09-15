package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * REST controller layer for order-service.
 *
 * @RefreshScope makes `greeting` reloadable at runtime: edit
 * config-repo/order-service.yml, commit it, then POST /actuator/busrefresh
 * on any bus-connected instance - Spring Cloud Bus broadcasts the refresh
 * event over the message broker and this bean is rebuilt with the new
 * value on every subscribed instance, with zero restarts / zero downtime.
 */
@RestController
@RequestMapping("/api/orders")
@RefreshScope
public class OrderController {

    @Value("${app.greeting:Hello, default greeting}")
    private String greeting;

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/greeting")
    public ResponseEntity<String> greeting() {
        return ResponseEntity.ok(greeting);
    }

    @PostMapping
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody Order order) {
        Order placed = orderService.placeOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(placed);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
