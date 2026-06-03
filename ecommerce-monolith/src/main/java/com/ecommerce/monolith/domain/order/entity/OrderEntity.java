package com.ecommerce.monolith.domain.order.entity;

import com.ecommerce.monolith.domain.order.enums.OrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate root.
 *
 * <p>Edge Case #8 — State Machine: all status changes go through transitionTo() which validates the
 * transition using OrderStatus.validateTransitionTo().
 */
@Entity
@Table(
    name = "orders",
    indexes = {
      @Index(name = "idx_order_user", columnList = "user_id"),
      @Index(name = "idx_order_status", columnList = "status"),
      @Index(name = "idx_order_idempotency", columnList = "idempotency_key", unique = true)
    })
public class OrderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OrderStatus status = OrderStatus.PENDING;

  @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalAmount;

  /**
   * Edge Case #1 — Idempotency Key: Stored on the order so we can detect duplicate order creation
   * requests. UNIQUE constraint in DB prevents two orders with the same idempotency key.
   */
  @Column(name = "idempotency_key", unique = true, length = 64)
  private String idempotencyKey;

  @Column(name = "shipping_address_id")
  private UUID shippingAddressId;

  @Column(name = "notes", length = 500)
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<OrderItemEntity> items = new ArrayList<>();

  protected OrderEntity() {}

  public static OrderEntity create(
      UUID userId, UUID shippingAddressId, String notes, String idempotencyKey) {
    OrderEntity o = new OrderEntity();
    o.userId = userId;
    o.shippingAddressId = shippingAddressId;
    o.notes = notes;
    o.idempotencyKey = idempotencyKey;
    o.createdAt = Instant.now();
    o.updatedAt = Instant.now();
    return o;
  }

  /**
   * Edge Case #8 — State Machine: The ONLY way to change order status. Validates transition is
   * allowed.
   */
  public void transitionTo(OrderStatus next) {
    this.status.validateTransitionTo(next); // throws if invalid
    this.status = next;
    this.updatedAt = Instant.now();
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getShippingAddressId() {
    return shippingAddressId;
  }

  public String getNotes() {
    return notes;
  }

  public List<OrderItemEntity> getItems() {
    return items;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
