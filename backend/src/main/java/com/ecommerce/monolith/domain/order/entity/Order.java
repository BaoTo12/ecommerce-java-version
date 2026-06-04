package com.ecommerce.monolith.domain.order.entity;

import com.ecommerce.monolith.common.status.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    // ─── Getters ──────────────────────────────────────────────────────────────
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
    @Column(name = "idempotency_key", unique = true)
    private UUID idempotencyKey;

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
    private List<OrderItem> items = new ArrayList<>();

    public static Order create(
            UUID userId, UUID shippingAddressId, String notes, UUID idempotencyKey) {
        Order o = new Order();
        o.userId = userId;
        o.shippingAddressId = shippingAddressId;
        o.notes = notes;
        o.idempotencyKey = idempotencyKey;
        o.createdAt = Instant.now();
        o.updatedAt = Instant.now();
        return o;
    }

    // Edge Case #8 — State Machine: The ONLY way to change order status. Validates transition is allowed.
    public void transitionTo(OrderStatus next) {
        this.status.validateTransitionTo(next); // throws if invalid
        this.status = next;
        this.updatedAt = Instant.now();
    }

}
