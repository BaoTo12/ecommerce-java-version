package com.ecommerce.monolith.domain.order.repository;

import com.ecommerce.monolith.domain.order.entity.CheckoutSession;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CheckoutSession c WHERE c.idempotencyKey = :id")
    Optional<CheckoutSession> findByIdForUpdate(@Param("id") UUID id);

    Optional<CheckoutSession> findByOrderId(UUID orderId);

    List<CheckoutSession> findByExpiresAt(java.time.Instant expiresAt);
}
