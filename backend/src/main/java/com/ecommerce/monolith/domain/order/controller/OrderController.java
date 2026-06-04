package com.ecommerce.monolith.domain.order.controller;

import com.ecommerce.monolith.domain.order.dto.*;
import com.ecommerce.monolith.domain.order.service.OrderService;
import com.ecommerce.monolith.common.resilience.RateLimit;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
  public ResponseEntity<CheckoutSessionResponse> checkoutSession(@Valid @RequestBody CheckoutRequest request){
    return ResponseEntity.ok(orderService.checkoutSession(request));
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
