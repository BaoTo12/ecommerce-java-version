package com.ecommerce.monolith.domain.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "order_items")
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 255)
  private String productName;

  @Column(nullable = false)
  private int quantity;

  /** Price at the time of order placement (from cart priceSnapshot). */
  @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;


  public static OrderItem create(
          Order order, UUID productId, String productName, int quantity, BigDecimal unitPrice) {
    OrderItem i = new OrderItem();
    i.order = order;
    i.productId = productId;
    i.productName = productName;
    i.quantity = quantity;
    i.unitPrice = unitPrice;
    i.createdAt = Instant.now();
    return i;
  }

}
