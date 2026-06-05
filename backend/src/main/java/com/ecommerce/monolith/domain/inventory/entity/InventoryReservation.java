package com.ecommerce.monolith.domain.inventory.entity;

import com.ecommerce.monolith.common.status.InventoryReservationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "inventory_reservations",
    indexes = {
      @Index(name = "idx_inv_res_order", columnList = "order_id"),
      @Index(name = "idx_inv_res_expiry", columnList = "expires_at, status")
    })
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservation {

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

  @Builder.Default
  @Column(name = "expires_at")
  private Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

  @Column(name = "released_at")
  private Instant releasedAt;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public void markReleased() {
    this.status = InventoryReservationStatus.FAILED;
    this.releasedAt = Instant.now();
  }
}
