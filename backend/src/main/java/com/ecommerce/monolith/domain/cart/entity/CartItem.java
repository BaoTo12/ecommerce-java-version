package com.ecommerce.monolith.domain.cart.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Shopping cart item.
 *
 * <p>Edge Case #4 — Price Snapshot: When the user adds a product to the cart, we record the
 * product's current price in `price_snapshot`. This freezes the price the user saw.
 *
 * <p>At checkout time, we compare the live price (from ProductEntity) against price_snapshot. If
 * they differ by more than the configured tolerance (e.g. 1%), the checkout is rejected with a
 * PRICE_CHANGED error, prompting the user to review their cart.
 *
 * <p>Why this matters: Without price snapshotting, a flash sale could end between "Add to Cart" and
 * "Checkout" and the user would be charged the higher post-sale price without their knowledge. Or
 * vice versa: prices could increase and the customer unfairly gets the old lower price.
 */
@Getter
@Entity
@Table(name = "cart_items")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

  // ─── Getters ──────────────────────────────────────────────────────────────
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cart_id", nullable = false)
  private Cart cart;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 255)
  private String productName;

  @Column(nullable = false)
  private int quantity;

  // Edge Case #4: Price at the time the item was added to cart. Compared against the live price at
  // checkout.
  @Column(name = "price_snapshot", nullable = false, precision = 15, scale = 2)
  private BigDecimal priceSnapshot;

  @Builder.Default
  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt = Instant.now();

  @Builder.Default
  @Column(name = "selected", nullable = false)
  private boolean selected = true;

  public void updateQuantity(int quantity) {
    this.quantity = quantity;
  }
}
