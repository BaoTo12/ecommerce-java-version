package com.ecommerce.order.controller;

import com.ecommerce.order.model.dto.CreateOrderRequest;
import com.ecommerce.order.model.dto.OrderHistoryResponse;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(req));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{orderId}/history")
    public ResponseEntity<List<OrderHistoryResponse>> getHistory(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderHistory(orderId));
    }
}
