package com.ecommerce.monolith.domain.inventory.entity;

import com.ecommerce.monolith.common.status.InventoryReservationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

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
@Getter
@Entity
@Table(
    name = "inventory_reservations",
    indexes = {
      @Index(name = "idx_inv_res_order", columnList = "order_id"),
      @Index(name = "idx_inv_res_expiry", columnList = "expires_at, status")
    })
@Builder
@AllArgsConstructor
public class InventoryReservation {

    // ─── Getters ──────────────────────────────────────────────────────────────
    @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id")
  private UUID orderId;

  @Builder.Default
  @Column(name = "operation", nullable = false, length = 50)
  private String operation = "RESERVE";

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  /** SUCCESS or FAILED */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InventoryReservationStatus status;

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

  protected InventoryReservation() {}

  public static InventoryReservation reserved(
      UUID orderId, UUID productId, int quantity, Instant expiresAt) {
    InventoryReservation r = new InventoryReservation();
    r.orderId = orderId;
    r.productId = productId;
    r.quantity = quantity;
    r.status = InventoryReservationStatus.SUCCEED;
    r.operation = "RESERVE";
    r.expiresAt = expiresAt;
    r.createdAt = Instant.now();
    return r;
  }

  public static InventoryReservation failed(
     UUID orderId, UUID productId, int quantity, String reason) {
    InventoryReservation r = new InventoryReservation();
    r.orderId = orderId;
    r.productId = productId;
    r.quantity = quantity;
    r.status = InventoryReservationStatus.FAILED;
    r.operation = "RESERVE";
    r.failureReason = reason;
    r.createdAt = Instant.now();
    return r;
  }

  public void markReleased() {
    this.status = InventoryReservationStatus.FAILED;
    this.releasedAt = Instant.now();
  }

  public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt) && releasedAt == null;
  }

}
