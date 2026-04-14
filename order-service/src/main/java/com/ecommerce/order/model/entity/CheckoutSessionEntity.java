package com.ecommerce.order.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_sessions")
public class CheckoutSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "subtotal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "final_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CheckoutSessionEntity() {}

    public static CheckoutSessionEntity create(UUID cartId, UUID userId, BigDecimal subtotal) {
        CheckoutSessionEntity s = new CheckoutSessionEntity();
        s.cartId = cartId;
        s.userId = userId;
        s.status = "CREATED";
        s.subtotalAmount = subtotal;
        s.finalAmount = subtotal;
        s.createdAt = Instant.now();
        return s;
    }

    public void processing() { this.status = "PROCESSING"; }
    public void complete(UUID orderId) {
        this.status = "COMPLETED";
        this.orderId = orderId;
        this.completedAt = Instant.now();
    }
    public void fail(String reason) {
        this.status = "FAILED";
        this.failureReason = reason;
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public UUID getCartId() { return cartId; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public UUID getOrderId() { return orderId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
