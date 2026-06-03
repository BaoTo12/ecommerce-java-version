package com.ecommerce.monolith.domain.payment.controller;

import com.ecommerce.monolith.domain.payment.dto.PaymentResponse;
import com.ecommerce.monolith.domain.payment.dto.RefundRequest;
import com.ecommerce.monolith.domain.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @GetMapping("/order/{orderId}")
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID orderId) {
    return ResponseEntity.ok(paymentService.getByOrderId(orderId));
  }

  @PostMapping("/order/{orderId}")
  public ResponseEntity<PaymentResponse> processPayment(
      @PathVariable UUID orderId, @RequestParam java.math.BigDecimal amount) {
    UUID userId = com.ecommerce.monolith.infrastructure.security.SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(paymentService.processPayment(orderId, userId, amount));
  }

  /**
   * Edge Case #10: Refund — only CHARGED payments can be refunded. Second call returns cached
   * result (idempotent via state machine).
   */
  @PostMapping("/order/{orderId}/refund")
  public ResponseEntity<PaymentResponse> refund(
      @PathVariable UUID orderId, @Valid @RequestBody RefundRequest req) {
    return ResponseEntity.ok(paymentService.refund(orderId, req));
  }
}
