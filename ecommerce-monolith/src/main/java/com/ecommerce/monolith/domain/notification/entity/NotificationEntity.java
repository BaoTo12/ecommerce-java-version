package com.ecommerce.monolith.domain.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Notification entity.
 *
 * <p>Edge Case #18 — Notification Deduplication: The UNIQUE constraint on (order_id, type) ensures
 * that even if the outbox poller fires multiple times for the same event (at-least-once delivery),
 * the customer only receives ONE email per event type per order.
 *
 * <p>Flow: 1. Outbox poller calls NotificationService.sendOrderConfirmed(orderId, userId) 2.
 * Service checks if notification with type=ORDER_CONFIRMED already exists AND is SENT 3. If yes →
 * return (dedup, no second email) 4. If no → create record, call email sender 5. If email sent →
 * mark SENT; if failed → increment retry_count
 *
 * <p>The UNIQUE constraint is a DB-level safeguard against the race condition where two poller
 * threads simultaneously check "does notification exist?" and both see no → both try to insert →
 * only one succeeds.
 */
@Entity
@Table(
    name = "notifications",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_notification_order_type",
          columnNames = {"order_id", "type"})
    },
    indexes = {
      @Index(name = "idx_notification_order", columnList = "order_id"),
      @Index(name = "idx_notification_user", columnList = "user_id")
    })
public class NotificationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** ORDER_CONFIRMED, ORDER_COMPLETED, ORDER_CANCELLED, PAYMENT_FAILED */
  @Column(nullable = false, length = 50)
  private String type;

  @Column(name = "recipient_email", nullable = false, length = 255)
  private String recipientEmail;

  @Column(nullable = false, length = 500)
  private String subject;

  /** PENDING, SENT, FAILED */
  @Column(nullable = false, length = 20)
  private String status = "PENDING";

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "max_retry", nullable = false)
  private int maxRetry = 3;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NotificationEntity() {}

  public static NotificationEntity create(
      UUID orderId, UUID userId, String type, String recipientEmail, String subject) {
    NotificationEntity n = new NotificationEntity();
    n.orderId = orderId;
    n.userId = userId;
    n.type = type;
    n.recipientEmail = recipientEmail;
    n.subject = subject;
    n.createdAt = Instant.now();
    n.updatedAt = Instant.now();
    return n;
  }

  public void markSent() {
    this.status = "SENT";
    this.sentAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void markFailed(String error) {
    this.retryCount++;
    this.lastError = error;
    if (this.retryCount >= this.maxRetry) {
      this.status = "FAILED";
    }
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

  public String getType() {
    return type;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getSubject() {
    return subject;
  }

  public String getStatus() {
    return status;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
