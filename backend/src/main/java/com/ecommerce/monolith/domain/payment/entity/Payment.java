package com.ecommerce.monolith.domain.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Payment entity.
 *
 * <p>Edge Case #3 — Pessimistic Locking: loaded with SELECT FOR UPDATE when processing to prevent
 * concurrent payment for same order. Edge Case #9 — Duplicate Payment Prevention: DB UNIQUE
 * constraint on order_id. Edge Case #10 — Refund State Machine: only CHARGED payments can be
 * refunded.
 */
@Getter
@Entity
@Table(
    name = "payments",
    indexes = {
      @Index(name = "idx_payment_order", columnList = "order_id", unique = true),
      @Index(name = "idx_payment_user", columnList = "user_id")
    })
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

  // ─── Getters ──────────────────────────────────────────────────────────────
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // Edge Case #9 — Duplicate Payment Prevention: The UNIQUE constraint on this column (set in
  // schema SQL) ensures that even if the payment service receives two concurrent requests for the
  // same order, only one payment row can be created. The second insert will throw
  // DataIntegrityViolationException, which is caught and handled gracefully (returns the existing
  // payment status).
  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  // Edge Case #10 — Refund State Machine: Valid transitions: PENDING → CHARGED (payment successful)
  // PENDING → FAILED (payment failed) CHARGED → REFUND_REQUESTED (customer requests refund)
  // REFUND_REQUESTED → REFUNDED (gateway confirms refund) REFUND_REQUESTED → REFUND_FAILED (gateway
  // refund failed)
  //
  // Refund is ONLY allowed from CHARGED state. Double-refund is prevented by: REFUNDED →
  // (nothing) is terminal.
  @Builder.Default
  @Column(nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "refund_reason", length = 500)
  private String refundReason;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Builder.Default
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public void markCharged() {
    this.status = "CHARGED";
    this.updatedAt = Instant.now();
  }

  public void markFailed(String reason) {
    this.status = "FAILED";
    this.failureReason = reason;
    this.updatedAt = Instant.now();
  }

  // Edge Case #10: Only call this after verifying status == CHARGED
  public void requestRefund(String reason) {
    if (!"CHARGED".equals(this.status)) {
      throw new IllegalStateException("Cannot refund payment in status: " + this.status);
    }
    this.status = "REFUND_REQUESTED";
    this.refundReason = reason;
    this.updatedAt = Instant.now();
  }

  // Edge Case #10: Terminal state — cannot be refunded again
  public void markRefunded() {
    this.status = "REFUNDED";
    this.updatedAt = Instant.now();
  }

  public void markRefundFailed(String reason) {
    this.status = "REFUND_FAILED";
    this.failureReason = reason;
    this.updatedAt = Instant.now();
  }
}
