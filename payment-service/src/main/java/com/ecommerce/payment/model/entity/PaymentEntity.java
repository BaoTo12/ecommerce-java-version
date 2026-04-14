package com.ecommerce.payment.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {}

    public static PaymentEntity create(UUID orderId, UUID userId, BigDecimal amount) {
        PaymentEntity p = new PaymentEntity();
        p.orderId = orderId;
        p.userId = userId;
        p.amount = amount;
        p.status = "PENDING";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void markCharged(String gatewayTxnId) {
        this.status = "CHARGED";
        this.gatewayTransactionId = gatewayTxnId;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markRefundRequested(String reason) {
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

    // --- Getters ---
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getGatewayTransactionId() { return gatewayTransactionId; }
    public String getFailureReason() { return failureReason; }
    public String getRefundReason() { return refundReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
