package com.ecommerce.order.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_read_model")
public class OrderReadModelEntity {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderReadModelEntity() {}

    public static OrderReadModelEntity create(UUID orderId, UUID userId, String status,
                                              BigDecimal totalAmount, int itemCount) {
        OrderReadModelEntity rm = new OrderReadModelEntity();
        rm.orderId = orderId;
        rm.userId = userId;
        rm.status = status;
        rm.totalAmount = totalAmount;
        rm.itemCount = itemCount;
        rm.lastEventAt = Instant.now();
        rm.updatedAt = Instant.now();
        return rm;
    }

    public void updateStatus(String status) {
        this.status = status;
        this.lastEventAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void updatePaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
        this.updatedAt = Instant.now();
    }

    // --- Getters ---
    public UUID getOrderId() { return orderId; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getPaymentStatus() { return paymentStatus; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getItemCount() { return itemCount; }
    public Instant getLastEventAt() { return lastEventAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
