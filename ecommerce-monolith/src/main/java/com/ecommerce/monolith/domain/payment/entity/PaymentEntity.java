package com.ecommerce.monolith.domain.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment entity.
 *
 * <p>Edge Case #3 — Pessimistic Locking: loaded with SELECT FOR UPDATE when processing to prevent
 * concurrent payment for same order. Edge Case #9 — Duplicate Payment Prevention: DB UNIQUE
 * constraint on order_id. Edge Case #10 — Refund State Machine: only CHARGED payments can be
 * refunded.
 */
@Entity
@Table(
    name = "payments",
    indexes = {
      @Index(name = "idx_payment_order", columnList = "order_id", unique = true),
      @Index(name = "idx_payment_user", columnList = "user_id")
    })
public class PaymentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * Edge Case #9 — Duplicate Payment Prevention: The UNIQUE constraint on this column (set in
   * schema SQL) ensures that even if the payment service receives two concurrent requests for the
   * same order, only one payment row can be created. The second insert will throw
   * DataIntegrityViolationException, which is caught and handled gracefully (returns the existing
   * payment status).
   */
  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  /**
   * Edge Case #10 — Refund State Machine: Valid transitions: PENDING → CHARGED (payment successful)
   * PENDING → FAILED (payment failed) CHARGED → REFUND_REQUESTED (customer requests refund)
   * REFUND_REQUESTED → REFUNDED (gateway confirms refund) REFUND_REQUESTED → REFUND_FAILED (gateway
   * refund failed)
   *
   * <p>Refund is ONLY allowed from CHARGED state. Double-refund is prevented by: REFUNDED →
   * (nothing) is terminal.
   */
  @Column(nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "gateway_transaction_id")
  private String gatewayTransactionId;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "refund_reason", length = 500)
  private String refundReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PaymentEntity() {}

  public static PaymentEntity create(UUID orderId, UUID userId, BigDecimal amount) {
    PaymentEntity p = new PaymentEntity();
    p.orderId = orderId;
    p.userId = userId;
    p.amount = amount;
    p.createdAt = Instant.now();
    p.updatedAt = Instant.now();
    return p;
  }

  public void markCharged(String gatewayTxnId) {
    this.status = "CHARGED";
    this.gatewayTransactionId = gatewayTxnId;
    this.updatedAt = Instant.now();
  }

  public void markFailed(String reason) {
    this.status = "FAILED";
    this.failureReason = reason;
    this.updatedAt = Instant.now();
  }

  /** Edge Case #10: Only call this after verifying status == CHARGED */
  public void requestRefund(String reason) {
    if (!"CHARGED".equals(this.status)) {
      throw new IllegalStateException("Cannot refund payment in status: " + this.status);
    }
    this.status = "REFUND_REQUESTED";
    this.refundReason = reason;
    this.updatedAt = Instant.now();
  }

  /** Edge Case #10: Terminal state — cannot be refunded again */
  public void markRefunded() {
    this.status = "REFUNDED";
    this.updatedAt = Instant.now();
  }

  public void markRefundFailed(String reason) {
    this.status = "REFUND_FAILED";
    this.failureReason = reason;
    this.updatedAt = Instant.now();
  }

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getUserId() {
    return userId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getStatus() {
    return status;
  }

  public String getGatewayTransactionId() {
    return gatewayTransactionId;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getRefundReason() {
    return refundReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
