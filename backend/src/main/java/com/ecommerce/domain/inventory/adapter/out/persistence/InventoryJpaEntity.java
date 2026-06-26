package com.ecommerce.domain.inventory.adapter.out.persistence;

import jakarta.persistence.*;
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
@Table(
    name = "inventory",
    indexes = {@Index(name = "idx_inventory_product", columnList = "product_id", unique = true)})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryJpaEntity {

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  @Version
  @Column(nullable = false)
  private long version;

  @Builder.Default
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
