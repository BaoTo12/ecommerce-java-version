package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {
    @Query("SELECT c FROM CartEntity c WHERE c.userId = :userId AND c.status = 'ACTIVE'")
    Optional<CartEntity> findActiveByUserId(UUID userId);
}
