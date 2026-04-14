package com.ecommerce.payment.integration.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

/**
 * Mock payment gateway — simulates 90% success / 10% failure (configurable).
 */
@Component
public class MockPaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayClient.class);
    private final double successRate;
    private final Random random = new Random();

    public MockPaymentGatewayClient(@Value("${app.payment.success-rate:0.9}") double successRate) {
        this.successRate = successRate;
    }

    public record GatewayResponse(boolean success, String transactionId, String failureReason) {}

    public GatewayResponse charge(UUID orderId, UUID userId, BigDecimal amount) {
        // Simulate processing delay
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (random.nextDouble() < successRate) {
            String txnId = "gw-txn-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("Payment CHARGED: orderId={}, amount={}, txnId={}", orderId, amount, txnId);
            return new GatewayResponse(true, txnId, null);
        } else {
            log.warn("Payment FAILED: orderId={}, amount={}", orderId, amount);
            return new GatewayResponse(false, null, "PAYMENT_DECLINED");
        }
    }

    public GatewayResponse refund(String gatewayTransactionId, BigDecimal amount) {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (random.nextDouble() < 0.95) {
            log.info("Refund SUCCESS: txnId={}, amount={}", gatewayTransactionId, amount);
            return new GatewayResponse(true, gatewayTransactionId, null);
        } else {
            log.warn("Refund FAILED: txnId={}", gatewayTransactionId);
            return new GatewayResponse(false, null, "REFUND_FAILED");
        }
    }
}
