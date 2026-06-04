package com.ecommerce.monolith.domain.cart.entity;

import com.ecommerce.monolith.common.status.CartStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shopping cart.
 *
 * <p>Edge Case #7 — Concurrent Cart Modification: When two devices (or browser tabs) try to modify
 * the cart simultaneously, without locking they can read the same state, both make changes, and one
 * will silently overwrite the other's changes.
 *
 * <p>Solution: CartRepository.findActiveByUserIdForUpdate() uses @Lock(PESSIMISTIC_WRITE) which
 * issues SELECT ... FOR UPDATE. The second request waits until the first one commits.
 */
@Getter
@Entity
@Table(
        name = "carts",
        indexes = {@Index(name = "idx_cart_user_status", columnList = "user_id, status")})
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CartStatus status = CartStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public static Cart create(UUID userId) {
        Cart c = new Cart();
        c.userId = userId;
        c.status = CartStatus.ACTIVE;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void markCheckedOut() {
        this.status = CartStatus.CHECKED_OUT;
        this.updatedAt = Instant.now();
    }

    public void markAbandoned() {
        this.status = CartStatus.ABANDONED;
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }


}
