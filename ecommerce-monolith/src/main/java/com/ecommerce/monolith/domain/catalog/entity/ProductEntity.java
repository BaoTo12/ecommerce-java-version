package com.ecommerce.monolith.domain.catalog.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Product catalog entity. Edge Case #4: price field — when a user adds to cart, the cart item
 * captures a priceSnapshot from this field. At checkout, the current price is re-read and compared
 * against the snapshot.
 */
@Entity
@Table(
    name = "products",
    indexes = {
      @Index(name = "idx_product_sku", columnList = "sku", unique = true),
      @Index(name = "idx_product_active", columnList = "is_active")
    })
public class ProductEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String sku;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  /**
   * Edge Case #4 — Price Snapshot: This is the live price. Cart items snapshot this value at
   * add-to-cart time. At checkout, we compare cart item's priceSnapshot vs this current price.
   */
  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal price;

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

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public boolean isActive() {
    return isActive;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // ─── Setters (for admin operations) ───────────────────────────────────────
  public void setSku(String sku) {
    this.sku = sku;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public void setActive(boolean active) {
    isActive = active;
  }
}
