package com.ecommerce.inventory.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations")
public class InventoryReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 10)
    private String operation;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryReservationEntity() {}

    public static InventoryReservationEntity success(UUID orderId, UUID productId, int qty, String operation) {
        InventoryReservationEntity e = new InventoryReservationEntity();
        e.orderId = orderId;
        e.productId = productId;
        e.quantity = qty;
        e.operation = operation;
        e.status = "SUCCESS";
        e.createdAt = Instant.now();
        return e;
    }

    public static InventoryReservationEntity failed(UUID orderId, UUID productId, int qty, String operation, String reason) {
        InventoryReservationEntity e = new InventoryReservationEntity();
        e.orderId = orderId;
        e.productId = productId;
        e.quantity = qty;
        e.operation = operation;
        e.status = "FAILED";
        e.failureReason = reason;
        e.createdAt = Instant.now();
        return e;
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public String getOperation() { return operation; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
