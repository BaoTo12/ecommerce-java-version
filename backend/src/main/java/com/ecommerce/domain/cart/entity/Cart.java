package com.ecommerce.domain.cart.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  public void touch() {
    this.updatedAt = Instant.now();
  }
}
