package com.ecommerce.inventory.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class InventoryEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryEntity() {}

    public void reserve(int qty) {
        if (this.quantity < qty) {
            throw new IllegalStateException("Insufficient stock: available=%d, requested=%d".formatted(quantity, qty));
        }
        this.quantity -= qty;
        this.updatedAt = Instant.now();
    }

    public void release(int qty) {
        this.quantity += qty;
        this.updatedAt = Instant.now();
    }

    public boolean hasSufficientStock(int requested) {
        return this.quantity >= requested;
    }

    // --- Getters ---
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
