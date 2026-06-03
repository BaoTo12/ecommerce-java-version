package com.ecommerce.monolith.domain.order.controller;

import com.ecommerce.monolith.domain.order.dto.*;
import com.ecommerce.monolith.domain.order.service.CheckoutService;
import com.ecommerce.monolith.domain.order.service.OrderService;
import com.ecommerce.monolith.infrastructure.resilience.RateLimit;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;
  private final CheckoutService checkoutService;

  public OrderController(OrderService orderService, CheckoutService checkoutService) {
    this.orderService = orderService;
    this.checkoutService = checkoutService;
  }

  /**
   * Edge Case #12: Rate limited — max 10 checkouts per minute per user. Edge Cases #1,4,16,17: All
   * handled inside CheckoutService.
   */
  @PostMapping("/checkout")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(maxRequests = 10, windowSeconds = 60, name = "checkout")
  public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest req) {
    return checkoutService.checkout(req);
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

  @GetMapping("/{orderId}/history")
  public List<OrderHistoryResponse> getHistory(@PathVariable UUID orderId) {
    return orderService.getOrderHistory(orderId);
  }
}
