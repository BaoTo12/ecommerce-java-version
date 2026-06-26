package com.ecommerce.domain.inventory.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataInventoryRepository extends JpaRepository<InventoryJpaEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM InventoryJpaEntity i WHERE i.productId = :productId")
  Optional<InventoryJpaEntity> findByIdPessimisticWrite(@Param("productId") UUID productId);

  @Modifying
  @Query(
      value =
          "UPDATE inventory SET quantity = quantity - :amount, updated_at = NOW() WHERE product_id = :productId AND quantity >= :amount",
      nativeQuery = true)
  int atomicDecrement(@Param("productId") UUID productId, @Param("amount") int amount);

  @Modifying
  @Query(
      value =
          "UPDATE inventory SET quantity = quantity + :amount, updated_at = NOW() WHERE product_id = :productId",
      nativeQuery = true)
  void atomicIncrement(@Param("productId") UUID productId, @Param("amount") int amount);
}
