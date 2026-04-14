package com.ecommerce.order.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItemEntity() {}

    public CartItemEntity(CartEntity cart, UUID productId, int quantity, BigDecimal unitPrice) {
        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void updateQuantity(int newQuantity) {
        this.quantity = newQuantity;
        this.updatedAt = Instant.now();
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public CartEntity getCart() { return cart; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
