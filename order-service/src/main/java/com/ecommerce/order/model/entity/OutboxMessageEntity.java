package com.ecommerce.order.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxMessageEntity() {}

    public static OutboxMessageEntity of(String eventType, String aggregateId, String topic, String payload) {
        OutboxMessageEntity m = new OutboxMessageEntity();
        m.eventType = eventType;
        m.aggregateId = aggregateId;
        m.topic = topic;
        m.payload = payload;
        m.createdAt = Instant.now();
        return m;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public boolean isPublished() { return published; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
}
