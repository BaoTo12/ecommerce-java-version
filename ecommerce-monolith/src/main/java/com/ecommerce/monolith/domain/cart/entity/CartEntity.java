package com.ecommerce.monolith.domain.cart.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shopping cart.
 *
 * <p>Edge Case #7 — Concurrent Cart Modification: When two devices (or browser tabs) try to modify
 * the cart simultaneously, without locking they can read the same state, both make changes, and one
 * will silently overwrite the other's changes.
 *
 * <p>Solution: CartRepository.findActiveByUserIdForUpdate() uses @Lock(PESSIMISTIC_WRITE) which
 * issues SELECT ... FOR UPDATE. The second request waits until the first one commits.
 */
@Entity
@Table(
    name = "carts",
    indexes = {@Index(name = "idx_cart_user_status", columnList = "user_id, status")})
public class CartEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE"; // ACTIVE, CHECKED_OUT, ABANDONED

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "cart",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<CartItemEntity> items = new ArrayList<>();

  protected CartEntity() {}

  public static CartEntity create(UUID userId) {
    CartEntity c = new CartEntity();
    c.userId = userId;
    c.createdAt = Instant.now();
    c.updatedAt = Instant.now();
    return c;
  }

  public void markCheckedOut() {
    this.status = "CHECKED_OUT";
    this.updatedAt = Instant.now();
  }

  public void markAbandoned() {
    this.status = "ABANDONED";
    this.updatedAt = Instant.now();
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getStatus() {
    return status;
  }

  public List<CartItemEntity> getItems() {
    return items;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
