package com.ecommerce.monolith.infrastructure.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Edge Case #11 — Outbox Pattern
 *
 * <p>Problem: If we persist an Order and then try to call another service (or publish an event to
 * Kafka), the following can happen: 1. Order saved ✓ 2. JVM crashes before publishing event ✗ →
 * Order created but inventory/payment never notified → inconsistent state.
 *
 * <p>Solution — Transactional Outbox: 1. Within the SAME DB transaction that saves the Order, also
 * insert a row into `outbox_messages`. 2. A background poller (OutboxPoller) reads unprocessed rows
 * and calls the target service method directly (within the monolith, this is just a direct
 * in-process call — no Kafka needed!). 3. If the poller fails, it retries up to `max_retry` times
 * with backoff.
 *
 * <p>In the monolith context "publishing an event" = calling the target service method. The outbox
 * still provides durability: if the app crashes between step 1 and the service call, on restart the
 * poller picks up the unpublished row and retries.
 */
@Entity
@Table(
    name = "outbox_messages",
    indexes = {@Index(name = "idx_outbox_pending", columnList = "published, created_at")})
public class OutboxMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Fully-qualified event type name. Determines which handler to call. */
  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  /** ID of the aggregate that produced this event (e.g., orderId). */
  @Column(name = "aggregate_id", nullable = false, length = 100)
  private String aggregateId;

  /** JSON payload of the event. */
  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(nullable = false)
  private boolean published = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "max_retry", nullable = false)
  private int maxRetry = 5;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  protected OutboxMessageEntity() {}

  public static OutboxMessageEntity of(String eventType, String aggregateId, String payload) {
    OutboxMessageEntity m = new OutboxMessageEntity();
    m.eventType = eventType;
    m.aggregateId = aggregateId;
    m.payload = payload;
    m.createdAt = Instant.now();
    m.nextRetryAt = Instant.now();
    return m;
  }

  public void markPublished() {
    this.published = true;
    this.publishedAt = Instant.now();
  }

  /**
   * Mark as failed with exponential backoff for next retry. Pattern: 1s → 2s → 4s → 8s → 16s
   * (doubles each attempt).
   */
  public void markFailed(String error) {
    this.retryCount++;
    this.lastError = error;
    long backoffSeconds = (long) Math.pow(2, retryCount);
    this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
  }

  public boolean isExhausted() {
    return retryCount >= maxRetry;
  }

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public String getEventType() {
    return eventType;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getPayload() {
    return payload;
  }

  public boolean isPublished() {
    return published;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getNextRetryAt() {
    return nextRetryAt;
  }
}
