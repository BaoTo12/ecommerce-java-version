package com.ecommerce.common.resilience;

import com.ecommerce.common.exception.DeadlockRetryException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;

/**
 * Edge Case #25 — Database Deadlock Detection and Retry
 *
 * <p>Problem: A deadlock occurs when two concurrent transactions are waiting on locks held by each
 * other — a circular dependency:
 *
 * <p>T1: LOCK row(product_A) ... waiting for LOCK row(product_B) T2: LOCK row(product_B) ...
 * waiting for LOCK row(product_A) → PostgreSQL detects the cycle, picks a victim (T2), rolls it
 * back → T2 gets: ERROR 40P01: deadlock detected
 *
 * <p>Classic e-commerce scenario (multi-product checkout): User X orders [product-1, product-2]
 * User Y orders [product-2, product-1] Both select in natural/random order → AB / BA lock ordering
 * → deadlock
 *
 * <p>Two complementary solutions are implemented:
 *
 * <p>Solution A — Consistent Lock Ordering (prevents deadlocks): Always acquire locks in the SAME
 * order (by product UUID ascending). If T1 and T2 both always lock product_1 first, then product_2:
 * T1 gets product_1, T2 waits. T1 gets product_2, commits. T2 proceeds. No circular dependency → no
 * deadlock. See: InventoryService.reserveForOrder() — items.sort(by productId) before locking.
 *
 * <p>Solution B — Deadlock Retry (handles residual deadlocks): Even with lock ordering, deadlocks
 * can still occur from other causes (page-level locks, index locks, constraint checks). Always have
 * a retry layer.
 *
 * <p>This class implements Solution B — a generic retry wrapper that catches Spring's
 * CannotAcquireLockException / DeadlockLoserDataAccessException (both wrap PostgreSQL's 40P01
 * deadlock error).
 */
public final class DeadlockRetryTemplate {

  private static final Logger log = LoggerFactory.getLogger(DeadlockRetryTemplate.class);

  private static final int MAX_RETRIES = 3;
  private static final long BASE_BACKOFF_MS = 30;

  private DeadlockRetryTemplate() {}

  /**
   * Executes the given supplier with automatic deadlock retry. The supplier should be a
   * complete @Transactional method call — the entire transaction is retried, not just the failing
   * statement.
   *
   * @param operation descriptive name for logging
   * @param supplier the transactional operation to execute
   * @param <T> return type
   * @return the result on success
   * @throws DeadlockRetryException if all retries are exhausted
   */
  public static <T> T execute(String operation, Supplier<T> supplier) {
    int attempt = 0;
    while (attempt < MAX_RETRIES) {
      try {
        return supplier.get();
      } catch (CannotAcquireLockException | DeadlockLoserDataAccessException e) {
        attempt++;
        log.warn("Deadlock detected in '{}': attempt {}/{}", operation, attempt, MAX_RETRIES);
        if (attempt >= MAX_RETRIES) {
          log.error("Deadlock retries exhausted for operation: {}", operation);
          throw new DeadlockRetryException("Deadlock retries exhausted for: " + operation, e);
        }
        // Jittered backoff: random between 0 and BASE_BACKOFF * 2^attempt
        long waitMs = (long) (Math.random() * BASE_BACKOFF_MS * (1L << attempt));
        try {
          Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    throw new DeadlockRetryException(
        "Deadlock retry loop exited unexpectedly for: " + operation, null);
  }
}
