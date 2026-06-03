package com.ecommerce.monolith.domain.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks inventory reservation attempts.
 *
 * <p>Edge Case #5 — Reservation TTL: When inventory is reserved for an order (RESERVE operation),
 * we set an `expires_at` timestamp. A scheduled job (InventoryExpiryJob) finds reservations past
 * their expiry that are still in PENDING/RESERVED state and releases the stock automatically.
 *
 * <p>Scenario: 1. User places order → inventory reserved, expires_at = now + 30min 2. User never
 * completes payment → order stuck in PAYMENT_PENDING 3. After 30min, InventoryExpiryJob runs: -
 * Finds this reservation - Releases the stock back - Marks the order as EXPIRED 4. The stock is
 * available for other customers again
 */
@Entity
@Table(
    name = "inventory_reservations",
    indexes = {
      @Index(name = "idx_inv_res_order", columnList = "order_id"),
      @Index(name = "idx_inv_res_expiry", columnList = "expires_at, status")
    })
public class InventoryReservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  /** RESERVE or RELEASE */
  @Column(nullable = false, length = 20)
  private String operation;

  /** SUCCESS or FAILED */
  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "failure_reason", length = 200)
  private String failureReason;

  /**
   * Edge Case #5 — TTL: If this reservation is not confirmed (payment succeeds) before expires_at,
   * the InventoryExpiryJob will release the held stock.
   */
  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InventoryReservationEntity() {}

  public static InventoryReservationEntity reserved(
      UUID orderId, UUID productId, int quantity, Instant expiresAt) {
    InventoryReservationEntity r = new InventoryReservationEntity();
    r.orderId = orderId;
    r.productId = productId;
    r.quantity = quantity;
    r.operation = "RESERVE";
    r.status = "SUCCESS";
    r.expiresAt = expiresAt;
    r.createdAt = Instant.now();
    return r;
  }

  public static InventoryReservationEntity failed(
      UUID orderId, UUID productId, int quantity, String reason) {
    InventoryReservationEntity r = new InventoryReservationEntity();
    r.orderId = orderId;
    r.productId = productId;
    r.quantity = quantity;
    r.operation = "RESERVE";
    r.status = "FAILED";
    r.failureReason = reason;
    r.createdAt = Instant.now();
    return r;
  }

  public static InventoryReservationEntity released(UUID orderId, UUID productId, int quantity) {
    InventoryReservationEntity r = new InventoryReservationEntity();
    r.orderId = orderId;
    r.productId = productId;
    r.quantity = quantity;
    r.operation = "RELEASE";
    r.status = "SUCCESS";
    r.releasedAt = Instant.now();
    r.createdAt = Instant.now();
    return r;
  }

  public void markReleased() {
    this.status = "RELEASED";
    this.releasedAt = Instant.now();
  }

  public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt) && releasedAt == null;
  }

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getProductId() {
    return productId;
  }

  public int getQuantity() {
    return quantity;
  }

  public String getOperation() {
    return operation;
  }

  public String getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getReleasedAt() {
    return releasedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
