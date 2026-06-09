package com.ecommerce.domain.order.controller;

import com.ecommerce.common.exception.AppException;
import com.ecommerce.common.resilience.RateLimit;
import com.ecommerce.domain.order.dto.CheckoutExecuteRequest;
import com.ecommerce.domain.order.dto.CheckoutSessionRequest;
import com.ecommerce.domain.order.dto.CheckoutSessionResponse;
import com.ecommerce.domain.order.dto.OrderResponse;
import com.ecommerce.monolith.domain.order.dto.*;
import com.ecommerce.domain.order.service.OrderService;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Edge Case #12: Rate limited — max 10 checkouts per minute per user.
    @PostMapping("/checkout/session")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(maxRequests = 10, windowSeconds = 60, name = "checkout")
    public ResponseEntity<CheckoutSessionResponse> checkoutSession(
            @Valid @RequestBody CheckoutSessionRequest request) {
        return ResponseEntity.ok(orderService.checkoutSession(request));
    }

    @PostMapping("/checkout/execute")
    public ResponseEntity<String> executePayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyStr,
            @RequestBody(required = false) CheckoutExecuteRequest request) {

        if (idempotencyKeyStr == null || idempotencyKeyStr.isBlank()) {
            throw new AppException("Missing Idempotency-Key header", HttpStatus.BAD_REQUEST);
        }

        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyStr.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException("Invalid Idempotency-Key format", HttpStatus.BAD_REQUEST);
        }

        String responseBody = orderService.checkout(idempotencyKey, request);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }

    @GetMapping
    public Page<OrderResponse> listOrders(@PageableDefault(size = 20) Pageable pageable) {
        return orderService.listOrders(pageable);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }
}
