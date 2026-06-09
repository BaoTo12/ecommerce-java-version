package com.ecommerce.common.exception;

/**
 * Edge Case #25 — Database Deadlock
 *
 * <p>Thrown when the database detects a deadlock and rolls back our transaction. PostgreSQL throws
 * SQLState 40P01 (deadlock_detected). Spring wraps this as CannotAcquireLockException.
 *
 * <p>Classic e-commerce deadlock scenario: Thread A: reserves product P1, then tries to reserve
 * product P2 Thread B: reserves product P2, then tries to reserve product P1 Result: A waits for B,
 * B waits for A → PostgreSQL kills one → DeadlockException
 *
 * <p>Solution: consistent lock ordering (always acquire locks in the same order). See
 * InventoryService.reserveForOrder() — products sorted by ID before locking.
 */
public class DeadlockRetryException extends RuntimeException {
  public DeadlockRetryException(String message, Throwable cause) {
    super(message, cause);
  }
}
