package com.ecommerce.domain.order.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataCheckoutSessionRepository extends JpaRepository<CheckoutSessionJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CheckoutSessionJpaEntity c WHERE c.idempotencyKey = :id")
    Optional<CheckoutSessionJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<CheckoutSessionJpaEntity> findByOrderId(UUID orderId);

    List<CheckoutSessionJpaEntity> findByExpiresAt(Instant expiresAt);
}
