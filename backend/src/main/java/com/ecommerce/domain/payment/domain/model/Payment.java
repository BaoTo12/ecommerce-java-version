package com.ecommerce.domain.payment.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private BigDecimal amount;
    
    @Builder.Default
    private String status = "PENDING";
    
    private String failureReason;
    private String refundReason;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public void markCharged() {
        this.status = "CHARGED";
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void requestRefund(String reason) {
        if (!"CHARGED".equals(this.status)) {
            throw new IllegalStateException("Cannot refund payment in status: " + this.status);
        }
        this.status = "REFUND_REQUESTED";
        this.refundReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markRefunded() {
        this.status = "REFUNDED";
        this.updatedAt = Instant.now();
    }

    public void markRefundFailed(String reason) {
        this.status = "REFUND_FAILED";
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }
}
