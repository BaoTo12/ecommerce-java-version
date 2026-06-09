package com.ecommerce.domain.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_order_type",
                        columnNames = {"order_id", "type"})
        },
        indexes = {
                @Index(name = "idx_notification_order", columnList = "order_id"),
                @Index(name = "idx_notification_user", columnList = "user_id")
        })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // ORDER_CONFIRMED, ORDER_COMPLETED, ORDER_CANCELLED, PAYMENT_FAILED
    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    // PENDING, SENT, FAILED
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Builder.Default
    @Column(name = "max_retry", nullable = false)
    private int maxRetry = 3;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public void markSent() {
        this.status = "SENT";
        this.sentAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= this.maxRetry) {
            this.status = "FAILED";
        }
        this.updatedAt = Instant.now();
    }
}
