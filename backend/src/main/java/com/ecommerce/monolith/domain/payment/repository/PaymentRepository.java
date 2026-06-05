package com.ecommerce.monolith.domain.payment.repository;

import com.ecommerce.monolith.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByOrderId(UUID orderId);

  // Edge Case #3 — Pessimistic Locking for Payment Processing:
  //
  // Problem: Payment gateway webhook fires twice (this happens more often than you'd think —
  // network timeouts cause retries). Without locking, both webhook handlers could simultaneously
  // read "status=PENDING" and both try to charge the card → double charge.
  //
  // Solution: When processing a payment, we load the payment row with SELECT ... FOR UPDATE. The
  // second concurrent handler is blocked at this query until the first commits. By then, status =
  // CHARGED, so the second handler sees the updated status and returns early.
  //
  // This is different from Edge Case #9 (UNIQUE constraint): - #9: prevents creating TWO payment
  // rows for the same order - #3: prevents processing ONE payment row TWICE concurrently
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
  Optional<Payment> findByOrderIdForUpdate(UUID orderId);
}
