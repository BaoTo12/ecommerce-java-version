package com.ecommerce.order.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusHistoryEntity() {}

    public static OrderStatusHistoryEntity of(UUID orderId, String fromStatus, String toStatus, String reason) {
        OrderStatusHistoryEntity h = new OrderStatusHistoryEntity();
        h.orderId = orderId;
        h.fromStatus = fromStatus;
        h.toStatus = toStatus;
        h.reason = reason;
        h.createdAt = Instant.now();
        return h;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
