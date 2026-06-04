package com.ecommerce.monolith.domain.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Inventory entity.
 *
 * <p>Edge Case #2 — Optimistic Locking (@Version): The @Version field is managed by JPA/Hibernate.
 * When two threads read the same inventory row concurrently and both try to decrement the quantity:
 * - Thread A reads version=5, reserves 2 units → tries to save with version=5 - Thread B reads
 * version=5, reserves 2 units → tries to save with version=5 - Thread A succeeds → version becomes
 * 6 - Thread B fails with ObjectOptimisticLockingFailureException (version mismatch) - Thread B
 * retries → reads fresh version=6 and checks stock correctly
 *
 * <p>Edge Case #14 — Atomic SQL Decrement (Flash Sales): For high-concurrency flash sales,
 * optimistic locking with retry can be too slow (many retries under load).
 * InventoryRepository.atomicDecrement() uses a native SQL UPDATE ... WHERE quantity >= :amount
 * RETURNING rows_affected. If 0 rows updated → out of stock. No retry loop needed.
 */
@Getter
@Entity
@Table(
        name = "inventory",
        indexes = {@Index(name = "idx_inventory_product", columnList = "product_id", unique = true)})
public class Inventory {

    // ─── Getters ──────────────────────────────────────────────────────────────
    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    /**
     * Edge Case #2: Optimistic concurrency control. Incremented by JPA on each update.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public static Inventory create(UUID productId, int quantity) {
        Inventory e = new Inventory();
        e.productId = productId;
        e.quantity = quantity;
        e.updatedAt = Instant.now();
        return e;
    }

    /**
     * Edge Case #2: Used with optimistic lock path
     */
    public boolean hasSufficientStock(int amount) {
        return this.quantity >= amount;
    }

    /**
     * Edge Case #2: Used with optimistic lock path
     */
    public void reserve(int amount) {
        if (this.quantity < amount) throw new IllegalStateException("Insufficient stock");
        this.quantity -= amount;
        this.updatedAt = Instant.now();
    }

    public void release(int amount) {
        this.quantity += amount;
        this.updatedAt = Instant.now();
    }

    public void adjustStock(int delta) {
        this.quantity = Math.max(0, this.quantity + delta);
        this.updatedAt = Instant.now();
    }

}
