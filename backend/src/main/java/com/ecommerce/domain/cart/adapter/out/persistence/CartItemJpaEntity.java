package com.ecommerce.domain.cart.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cart_items")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cart_id", nullable = false)
  private CartJpaEntity cart;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 255)
  private String productName;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "price_snapshot", nullable = false, precision = 15, scale = 2)
  private BigDecimal priceSnapshot;

  @Builder.Default
  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt = Instant.now();

  @Builder.Default
  @Column(name = "selected", nullable = false)
  private boolean selected = true;
}
