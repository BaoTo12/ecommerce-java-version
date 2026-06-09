package com.ecommerce.domain.inventory.repository;

import com.ecommerce.domain.inventory.entity.Inventory;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
  Optional<Inventory> findByIdPessimisticWrite(UUID productId);


  @Modifying
  @Query(
      value =
          """
        UPDATE inventory
        SET quantity = quantity - :amount,
            updated_at = NOW()
        WHERE product_id = :productId
          AND quantity >= :amount
        """,
      nativeQuery = true)
  int atomicDecrement(UUID productId, int amount);

  @Modifying
  @Query(
      value =
          """
        UPDATE inventory
        SET quantity = quantity + :amount,
            updated_at = NOW()
        WHERE product_id = :productId
        """,
      nativeQuery = true)
  void atomicIncrement(UUID productId, int amount);
}
