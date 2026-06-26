package com.ecommerce.domain.cart.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataCartRepository extends JpaRepository<CartJpaEntity, UUID> {

  @Query("SELECT c FROM CartJpaEntity c WHERE c.userId = :userId")
  Optional<CartJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM CartJpaEntity c WHERE c.userId = :userId")
  Optional<CartJpaEntity> findActiveByUserIdForUpdate(@Param("userId") UUID userId);
}
