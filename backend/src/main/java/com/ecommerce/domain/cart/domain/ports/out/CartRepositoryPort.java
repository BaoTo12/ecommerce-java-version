package com.ecommerce.domain.cart.domain.ports.out;

import com.ecommerce.domain.cart.domain.model.Cart;
import java.util.Optional;
import java.util.UUID;

public interface CartRepositoryPort {
    Optional<Cart> findActiveByUserId(UUID userId);
    Optional<Cart> findActiveByUserIdForUpdate(UUID userId);
    Cart save(Cart cart);
    Optional<Cart> findById(UUID id);
}
