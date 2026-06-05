package com.ecommerce.monolith.domain.cart.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@Getter
@Entity
@Table(
    name = "carts",
    indexes = {@Index(name = "idx_cart_user_status", columnList = "user_id, status")})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Builder.Default
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Builder.Default
  @OneToMany(
      mappedBy = "cart",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<CartItem> items = new ArrayList<>();

  public void markCheckedOut() {
    this.updatedAt = Instant.now();
  }

  public void markAbandoned() {
    this.updatedAt = Instant.now();
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }
}
