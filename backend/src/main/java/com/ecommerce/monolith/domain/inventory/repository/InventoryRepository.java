package com.ecommerce.monolith.domain.inventory.repository;

import com.ecommerce.monolith.domain.inventory.entity.Inventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

  // Edge Case #14 — Atomic SQL Decrement (Flash Sales):
  //
  // Problem: In a flash sale, 10,000 concurrent users try to buy the last item. With ORM
  // read-then-write (even with optimistic locking), we get retry storms: - 9,999 threads fail with
  // ObjectOptimisticLockingFailureException - They all retry → even more collisions - The DB
  // becomes a thundering herd problem
  //
  // Solution: Single native SQL UPDATE with a WHERE clause that atomically checks AND decrements
  // the quantity. The DB engine serializes this at the row level. Only one thread gets
  // rows_affected=1; all others get 0.
  //
  // UPDATE inventory SET quantity = quantity - :amount, updated_at = now() WHERE product_id =
  // :productId AND quantity >= :amount
  //
  // Returns: 1 if success (enough stock), 0 if out of stock. No retry loop needed. No optimistic
  // lock exception. Scales to any number of concurrent requests.
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
  int atomicIncrement(UUID productId, int amount);
}
