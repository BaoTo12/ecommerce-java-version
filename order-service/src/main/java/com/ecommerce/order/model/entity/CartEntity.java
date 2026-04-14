package com.ecommerce.order.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class CartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItemEntity> items = new ArrayList<>();

    protected CartEntity() {}

    public static CartEntity create(UUID userId) {
        CartEntity c = new CartEntity();
        c.userId = userId;
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void checkOut() {
        this.status = "CHECKED_OUT";
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    // --- Getters ---
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public List<CartItemEntity> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
