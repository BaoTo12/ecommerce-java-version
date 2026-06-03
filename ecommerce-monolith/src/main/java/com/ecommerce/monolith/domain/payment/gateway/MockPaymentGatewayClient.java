package com.ecommerce.monolith.domain.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock payment gateway — simulates real payment provider behavior. Replace with real
 * Stripe/PayOS/VNPay client in production.
 */
@Component
public class MockPaymentGatewayClient {

  private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayClient.class);

  @Value("${app.payment.mock-success-rate:0.9}")
  private double successRate;

  public GatewayResponse charge(UUID orderId, UUID userId, BigDecimal amount) {
    log.info("Mock gateway: charging {} for order={}", amount, orderId);
    if (ThreadLocalRandom.current().nextDouble() < successRate) {
      return new GatewayResponse(
          true, "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null);
    }
    return new GatewayResponse(false, null, "CARD_DECLINED");
  }

  public GatewayResponse refund(String transactionId, BigDecimal amount) {
    log.info("Mock gateway: refunding txn={}, amount={}", transactionId, amount);
    if (ThreadLocalRandom.current().nextDouble() < 0.95) {
      return new GatewayResponse(
          true, "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null);
    }
    return new GatewayResponse(false, null, "REFUND_GATEWAY_ERROR");
  }

  public record GatewayResponse(boolean success, String transactionId, String failureReason) {}
}
