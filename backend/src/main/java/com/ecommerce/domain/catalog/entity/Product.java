package com.ecommerce.domain.catalog.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(
    name = "products",
    indexes = {
      @Index(name = "idx_product_sku", columnList = "sku", unique = true),
      @Index(name = "idx_product_active", columnList = "is_active")
    })
public class Product {

  // ─── Getters ──────────────────────────────────────────────────────────────
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // ─── Setters (for admin operations) ───────────────────────────────────────
  @Setter
  @Column(nullable = false, unique = true, length = 64)
  private String sku;

  @Setter
  @Column(nullable = false, length = 255)
  private String name;

  @Setter
  @Column(columnDefinition = "text")
  private String description;

  // Edge Case #4 — Price Snapshot: This is the live price. Cart items snapshot this value at
  // add-to-cart time. At checkout, we compare cart item's priceSnapshot vs this current price.
  @Setter
  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal price;

  @Setter
  @Column(name = "category", length = 100)
  private String category;

  @Setter
  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
