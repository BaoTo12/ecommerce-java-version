package com.ecommerce.monolith.domain.order.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private OrderEntity order;

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

  protected OrderItemEntity() {}

  public static OrderItemEntity create(
      OrderEntity order, UUID productId, String productName, int quantity, BigDecimal unitPrice) {
    OrderItemEntity i = new OrderItemEntity();
    i.order = order;
    i.productId = productId;
    i.productName = productName;
    i.quantity = quantity;
    i.unitPrice = unitPrice;
    i.createdAt = Instant.now();
    return i;
  }

  public UUID getId() {
    return id;
  }

  public OrderEntity getOrder() {
    return order;
  }

  public UUID getProductId() {
    return productId;
  }

  public String getProductName() {
    return productName;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
