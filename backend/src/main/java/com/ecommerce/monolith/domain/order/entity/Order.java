package com.ecommerce.monolith.domain.order.entity;

import com.ecommerce.monolith.common.status.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_order_user", columnList = "user_id"),
                @Index(name = "idx_order_status", columnList = "status"),
                @Index(name = "idx_order_idempotency", columnList = "idempotency_key", unique = true)
        })
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    // Edge Case #1 — Idempotency Key: Stored on the order so we can detect duplicate order creation
    // requests. UNIQUE constraint in DB prevents two orders with the same idempotency key.
    @Column(name = "idempotency_key", unique = true)
    private UUID idempotencyKey;

    @Column(name = "shipping_address_id")
    private UUID shippingAddressId;

    @Column(name = "notes", length = 500)
    private String notes;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Builder.Default
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    // Edge Case #8 — State Machine: The ONLY way to change order status. Validates transition is allowed
    public void transitionTo(OrderStatus next) {
        this.status.validateTransitionTo(next); // throws if invalid
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
