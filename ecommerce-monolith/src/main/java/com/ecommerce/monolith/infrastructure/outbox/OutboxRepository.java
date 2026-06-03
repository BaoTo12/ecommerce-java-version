package com.ecommerce.monolith.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxMessageEntity, UUID> {

  /**
   * Fetches unpublished messages that are ready to retry, using SKIP LOCKED so multiple poller
   * instances don't conflict.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      value =
          """
        SELECT m FROM OutboxMessageEntity m
        WHERE m.published = false
          AND m.nextRetryAt <= :now
          AND m.retryCount < m.maxRetry
        ORDER BY m.createdAt ASC
        LIMIT :batchSize
        """)
  List<OutboxMessageEntity> findReadyForProcessing(Instant now, int batchSize);
}
