package com.ecommerce.monolith.domain.cart.repository;

import com.ecommerce.monolith.domain.cart.entity.CartEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {

  @Query("SELECT c FROM CartEntity c WHERE c.userId = :userId AND c.status = 'ACTIVE'")
  Optional<CartEntity> findActiveByUserId(UUID userId);

  /**
   * Edge Case #7 — Concurrent Cart Modification: SELECT ... FOR UPDATE on the cart row. When two
   * requests try to modify the same cart simultaneously: - Request A gets the lock, proceeds -
   * Request B waits at this query until A commits - Request B then sees A's changes and makes its
   * own modification This prevents lost updates where one change silently overwrites another.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM CartEntity c WHERE c.userId = :userId AND c.status = 'ACTIVE'")
  Optional<CartEntity> findActiveByUserIdForUpdate(UUID userId);
}
