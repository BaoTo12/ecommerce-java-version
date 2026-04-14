package com.ecommerce.notification.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationEntity() {}

    public static NotificationEntity create(UUID orderId, UUID userId, String type, String subject) {
        NotificationEntity n = new NotificationEntity();
        n.orderId = orderId;
        n.userId = userId;
        n.type = type;
        n.recipientEmail = userId + "@example.com"; // placeholder — no real user service
        n.subject = subject;
        n.createdAt = Instant.now();
        n.updatedAt = Instant.now();
        return n;
    }

    public void markSent() {
        this.status = "SENT";
        this.sentAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= 3) {
            this.status = "FAILED";
        }
        this.updatedAt = Instant.now();
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getUserId() { return userId; }
    public String getType() { return type; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSubject() { return subject; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
