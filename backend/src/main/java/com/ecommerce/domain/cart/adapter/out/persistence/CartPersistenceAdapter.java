package com.ecommerce.domain.cart.adapter.out.persistence;

import com.ecommerce.domain.cart.domain.model.Cart;
import com.ecommerce.domain.cart.domain.ports.out.CartRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CartPersistenceAdapter implements CartRepositoryPort {

    private final SpringDataCartRepository repository;

    public CartPersistenceAdapter(SpringDataCartRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Cart> findActiveByUserId(UUID userId) {
        return repository.findActiveByUserId(userId).map(CartPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Cart> findActiveByUserIdForUpdate(UUID userId) {
        return repository.findActiveByUserIdForUpdate(userId).map(CartPersistenceMapper::toDomain);
    }

    @Override
    public Cart save(Cart cart) {
        CartJpaEntity jpa = CartPersistenceMapper.toJpa(cart);
        CartJpaEntity saved = repository.save(jpa);
        return CartPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Cart> findById(UUID id) {
        return repository.findById(id).map(CartPersistenceMapper::toDomain);
    }
}
