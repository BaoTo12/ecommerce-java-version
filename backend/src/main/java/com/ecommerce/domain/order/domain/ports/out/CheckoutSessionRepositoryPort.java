package com.ecommerce.domain.order.domain.ports.out;

import com.ecommerce.domain.order.domain.model.CheckoutSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutSessionRepositoryPort {
    Optional<CheckoutSession> findByIdForUpdate(UUID id);
    Optional<CheckoutSession> findByOrderId(UUID orderId);
    List<CheckoutSession> findByExpiresAt(Instant expiresAt);
    Optional<CheckoutSession> findById(UUID id);
    CheckoutSession save(CheckoutSession session);
}
