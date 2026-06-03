package com.ecommerce.monolith.domain.payment.service;

import com.ecommerce.monolith.domain.order.entity.OrderStatusHistoryEntity;
import com.ecommerce.monolith.domain.order.enums.OrderStatus;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.order.repository.OrderStatusHistoryRepository;
import com.ecommerce.monolith.domain.payment.dto.PaymentResponse;
import com.ecommerce.monolith.domain.payment.dto.RefundRequest;
import com.ecommerce.monolith.domain.payment.entity.PaymentEntity;
import com.ecommerce.monolith.domain.payment.gateway.MockPaymentGatewayClient;
import com.ecommerce.monolith.domain.payment.repository.PaymentRepository;
import com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.infrastructure.exception.ResourceNotFoundException;
import com.ecommerce.monolith.infrastructure.outbox.OutboxService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment Service
 *
 * <p>Edge Case #3 — Pessimistic Locking: SELECT FOR UPDATE prevents double-charge Edge Case #9 —
 * Duplicate Payment Prevention: DB UNIQUE on order_id + check Edge Case #10 — Refund State Machine:
 * CHARGED → REFUNDED path only
 */
@Service
@Transactional
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentRepository paymentRepo;
  private final OrderRepository orderRepo;
  private final OrderStatusHistoryRepository historyRepo;
  private final MockPaymentGatewayClient gateway;
  private final OutboxService outboxService;

  public PaymentService(
      PaymentRepository paymentRepo,
      OrderRepository orderRepo,
      OrderStatusHistoryRepository historyRepo,
      MockPaymentGatewayClient gateway,
      OutboxService outboxService) {
    this.paymentRepo = paymentRepo;
    this.orderRepo = orderRepo;
    this.historyRepo = historyRepo;
    this.gateway = gateway;
    this.outboxService = outboxService;
  }

  /**
   * Process payment for an order.
   *
   * <p>Edge Case #3: SELECT FOR UPDATE on payment row (findByOrderIdForUpdate). Prevents two
   * concurrent calls from both seeing "PENDING" and charging twice.
   *
   * <p>Edge Case #9: DB UNIQUE constraint on order_id as last-resort guard. If somehow two new
   * PaymentEntity rows are created concurrently, the second INSERT will fail with
   * DataIntegrityViolationException.
   */
  public PaymentResponse processPayment(UUID orderId, UUID userId, BigDecimal amount) {
    // ─── Edge Case #3: Pessimistic lock ─────────────────────────────────
    // If no existing payment, create one. If one exists, load it with lock.
    PaymentEntity payment;
    try {
      payment =
          paymentRepo
              .findByOrderIdForUpdate(orderId)
              .orElseGet(
                  () -> paymentRepo.saveAndFlush(PaymentEntity.create(orderId, userId, amount)));
    } catch (DataIntegrityViolationException e) {
      // Edge Case #9: Race condition → another thread just created it
      log.warn("Race condition on payment creation for order={}. Loading existing.", orderId);
      payment =
          paymentRepo
              .findByOrderId(orderId)
              .orElseThrow(() -> new RuntimeException("Payment not found after conflict"));
    }
    // ────────────────────────────────────────────────────────────────────

    // Edge Case #9: Already processed → idempotent return
    if (!"PENDING".equals(payment.getStatus())) {
      log.info("Payment already processed for order={}, status={}", orderId, payment.getStatus());
      return toResponse(payment);
    }

    // Call mock payment gateway
    MockPaymentGatewayClient.GatewayResponse result = gateway.charge(orderId, userId, amount);

    if (result.success()) {
      payment.markCharged(result.transactionId());
      paymentRepo.save(payment);

      // Update order status via state machine (Edge Case #8)
      orderRepo
          .findById(orderId)
          .ifPresent(
              order -> {
                order.transitionTo(OrderStatus.PAID);
                orderRepo.save(order);
                historyRepo.save(
                    OrderStatusHistoryEntity.of(
                        orderId,
                        OrderStatus.PAYMENT_PENDING.name(),
                        OrderStatus.PAID.name(),
                        "Payment successful: " + result.transactionId()));
              });

      // Write notification event to outbox (Edge Case #11)
      outboxService.save(
          "OrderCompletedNotification",
          orderId.toString(),
          Map.of("orderId", orderId, "userId", userId));

      log.info("Payment succeeded: order={}, txn={}", orderId, result.transactionId());
    } else {
      payment.markFailed(result.failureReason());
      paymentRepo.save(payment);

      orderRepo
          .findById(orderId)
          .ifPresent(
              order -> {
                order.transitionTo(OrderStatus.PAYMENT_FAILED);
                orderRepo.save(order);
              });

      // Write notification event to outbox (Edge Case #11)
      outboxService.save(
          "PaymentFailedNotification",
          orderId.toString(),
          Map.of("orderId", orderId, "userId", userId, "failureReason", result.failureReason()));

      log.warn("Payment failed: order={}, reason={}", orderId, result.failureReason());
    }

    return toResponse(payment);
  }

  /**
   * Edge Case #10 — Refund State Machine + Idempotency:
   *
   * <p>1. Load with SELECT FOR UPDATE (prevents concurrent refund processing) 2. Check status is
   * CHARGED (state machine validation) 3. Call gateway refund 4. Terminal states: REFUNDED /
   * REFUND_FAILED cannot be refunded again
   *
   * <p>Second call with same orderId: sees status = REFUNDED → returns early. This prevents double
   * refunds even if the webhook fires twice.
   */
  public PaymentResponse refund(UUID orderId, RefundRequest req) {
    // ─── Edge Case #3: Pessimistic lock during refund ────────────────────
    PaymentEntity payment =
        paymentRepo
            .findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));
    // ────────────────────────────────────────────────────────────────────

    // ─── Edge Case #10: State machine — only CHARGED can be refunded ─────
    if ("REFUNDED".equals(payment.getStatus())) {
      log.info("Refund already completed for order={}", orderId);
      return toResponse(payment);
    }
    if ("REFUND_REQUESTED".equals(payment.getStatus())) {
      log.info("Refund already in progress for order={}", orderId);
      return toResponse(payment);
    }
    if (!"CHARGED".equals(payment.getStatus())) {
      throw new BusinessRuleViolationException(
          "Cannot refund payment in status: "
              + payment.getStatus()
              + ". Only CHARGED payments can be refunded.");
    }
    // ────────────────────────────────────────────────────────────────────

    payment.requestRefund(req.reason());

    MockPaymentGatewayClient.GatewayResponse result =
        gateway.refund(payment.getGatewayTransactionId(), payment.getAmount());

    if (result.success()) {
      payment.markRefunded();
      paymentRepo.save(payment);

      orderRepo
          .findById(orderId)
          .ifPresent(
              order -> {
                if (order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
                  order.transitionTo(OrderStatus.CANCELLED);
                  orderRepo.save(order);
                }
              });

      log.info("Refund succeeded: order={}", orderId);
    } else {
      payment.markRefundFailed(result.failureReason());
      paymentRepo.save(payment);
      log.error(
          "Refund FAILED — MANUAL INTERVENTION REQUIRED: order={}, reason={}",
          orderId,
          result.failureReason());
    }

    return toResponse(payment);
  }

  @Transactional(readOnly = true)
  public PaymentResponse getByOrderId(UUID orderId) {
    return paymentRepo
        .findByOrderId(orderId)
        .map(this::toResponse)
        .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));
  }

  private PaymentResponse toResponse(PaymentEntity p) {
    return new PaymentResponse(
        p.getOrderId(),
        p.getStatus(),
        p.getAmount(),
        p.getGatewayTransactionId(),
        p.getFailureReason(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
