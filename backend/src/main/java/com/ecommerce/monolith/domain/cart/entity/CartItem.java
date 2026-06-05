package com.ecommerce.monolith.domain.cart.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cart_items")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

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
