package com.ecommerce.monolith.infrastructure.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Edge Case #26 — Dead Letter Pattern for Outbox
 *
 * <p>Problem: An outbox message fails 5 times (max_retry exhausted). Options: a) Delete it → event
 * is silently lost forever (terrible) b) Keep it in outbox_messages → poller keeps trying, wastes
 * resources c) Move it to dead_letter_messages → isolated, alertable, replayable
 *
 * <p>Why a Dead Letter Queue (DLQ)? - The message is not lost — it's in a dedicated table for
 * investigation - Operations can see ALL failed events in one place - Events can be replayed after
 * the root cause is fixed - Active outbox poller is not polluted with permanently failed rows - An
 * alerting job can trigger PagerDuty/Slack when this table grows
 *
 * <p>Real-world scenario: Email provider goes down for 2 hours. - Outbox messages exhaust their
 * retries - They move to dead_letter_messages - Provider comes back up - Operations replays the DLQ
 * → all notifications sent - No data loss, no manual intervention on individual records
 */
@Getter
@Entity
@Table(
    name = "dead_letter_messages",
    indexes = {
      @Index(name = "idx_dlm_event_type", columnList = "event_type"),
      @Index(name = "idx_dlm_created_at", columnList = "created_at")
    })
public class DeadLetterMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "original_outbox_id", nullable = false)
  private UUID originalOutboxId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "aggregate_id", nullable = false, length = 100)
  private String aggregateId;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "failure_reason", nullable = false, columnDefinition = "text")
  private String failureReason;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Set when an operator manually replays this message. */
  @Column(name = "replayed_at")
  private Instant replayedAt;

  @Column(name = "replayed_by", length = 100)
  private String replayedBy;

  protected DeadLetterMessageEntity() {}

  public static DeadLetterMessageEntity from(OutboxMessageEntity outbox) {
    DeadLetterMessageEntity dlm = new DeadLetterMessageEntity();
    dlm.originalOutboxId = outbox.getId();
    dlm.eventType = outbox.getEventType();
    dlm.aggregateId = outbox.getAggregateId();
    dlm.payload = outbox.getPayload();
    dlm.failureReason = outbox.getLastError();
    dlm.retryCount = outbox.getRetryCount();
    dlm.createdAt = Instant.now();
    return dlm;
  }

  public void markReplayed(String operatorId) {
    this.replayedAt = Instant.now();
    this.replayedBy = operatorId;
  }
}
