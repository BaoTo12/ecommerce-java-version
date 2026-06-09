package com.ecommerce.domain.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "inventory",
    indexes = {@Index(name = "idx_inventory_product", columnList = "product_id", unique = true)})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  // Edge Case #2: Optimistic concurrency control. Incremented by JPA on each update.
  @Version
  @Column(nullable = false)
  private long version;

  @Builder.Default
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  // Edge Case #2: Used with optimistic lock path
  public boolean hasSufficientStock(int amount) {
    return this.quantity >= amount;
  }

  // Edge Case #2: Used with optimistic lock path
  public void reserve(int amount) {
    if (this.quantity < amount) throw new IllegalStateException("Insufficient stock");
    this.quantity -= amount;
    this.updatedAt = Instant.now();
  }
}
