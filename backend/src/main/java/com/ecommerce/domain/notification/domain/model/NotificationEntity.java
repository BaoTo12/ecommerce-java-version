package com.ecommerce.domain.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private String type;
    private String recipientEmail;
    private String subject;
    
    @Builder.Default
    private String status = "PENDING";
    
    @Builder.Default
    private int retryCount = 0;
    
    @Builder.Default
    private int maxRetry = 3;
    
    private String lastError;
    private Instant sentAt;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Builder.Default
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
