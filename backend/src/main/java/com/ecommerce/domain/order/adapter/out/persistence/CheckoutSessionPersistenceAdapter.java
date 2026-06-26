package com.ecommerce.domain.order.adapter.out.persistence;

import com.ecommerce.domain.order.domain.model.CheckoutSession;
import com.ecommerce.domain.order.domain.ports.out.CheckoutSessionRepositoryPort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CheckoutSessionPersistenceAdapter implements CheckoutSessionRepositoryPort {

    private final SpringDataCheckoutSessionRepository repository;

    public CheckoutSessionPersistenceAdapter(SpringDataCheckoutSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CheckoutSession> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    public Optional<CheckoutSession> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    public List<CheckoutSession> findByExpiresAt(Instant expiresAt) {
        return repository.findByExpiresAt(expiresAt).stream()
                .map(OrderPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CheckoutSession> findById(UUID id) {
        return repository.findById(id).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    public CheckoutSession save(CheckoutSession session) {
        CheckoutSessionJpaEntity jpa = OrderPersistenceMapper.toJpa(session);
        CheckoutSessionJpaEntity saved = repository.save(jpa);
        return OrderPersistenceMapper.toDomain(saved);
    }
}
