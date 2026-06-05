package com.ecommerce.monolith.domain.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Mock payment gateway — simulates real payment provider behavior
@Component
public class MockPaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayClient.class);

    @Value("${app.payment.mock-success-rate:0.9}")
    private double successRate;

    public GatewayResponse charge(UUID orderId, UUID userId, BigDecimal amount) {
        return charge(orderId, userId, amount, null, null, null, null, null);
    }

    public GatewayResponse charge(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String cardNumber,
            String cvc,
            String cardName,
            String expiry,
            String strategy) {
        log.info(
                "Mock gateway charge for order={}, amount={}, card={}, strategy={}",
                orderId,
                amount,
                cardNumber != null ? "masked" : "none",
                strategy);

        // Apply explicit strategy if selected
        if (strategy != null && !strategy.isBlank()) {
            switch (strategy.toUpperCase()) {
                case "DECLINED" -> {
                    return new GatewayResponse(false, null, "CARD_DECLINED");
                }
                case "INSUFFICIENT_FUNDS" -> {
                    return new GatewayResponse(false, null, "INSUFFICIENT_FUNDS");
                }
                case "EXPIRED_CARD" -> {
                    return new GatewayResponse(false, null, "EXPIRED_CARD");
                }
                case "INCORRECT_CVC" -> {
                    return new GatewayResponse(false, null, "INCORRECT_CVC");
                }
                case "TIMEOUT" -> {
                    try {
                        Thread.sleep(3000); // Simulate network latency/timeout
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new GatewayResponse(false, null, "GATEWAY_TIMEOUT");
                }
                case "SUCCEED" -> {
                    return new GatewayResponse(
                            true, "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null);
                }
            }
        }

        // Default card-number-based mock behavior if cardNumber is provided
        if (cardNumber != null && !cardNumber.isBlank()) {
            String cleanCard = cardNumber.replaceAll("\\D", "");

            // Luhn Check validation (except for the standard stripe success card or force fail cards)
            boolean isSpecialMockCard =
                    cleanCard.equals("4242424242424242")
                            || cleanCard.equals("4000000000000002")
                            || cleanCard.equals("4000000000003022")
                            || cleanCard.equals("4000000000000115")
                            || cleanCard.equals("4000000000000123");

            if (!isSpecialMockCard && !checkLuhn(cleanCard)) {
                return new GatewayResponse(false, null, "INVALID_CARD_NUMBER");
            }

            // Check card patterns
            if (cleanCard.equals("4000000000000002")) {
                return new GatewayResponse(false, null, "CARD_DECLINED");
            }
            if (cleanCard.equals("4000000000003022")) {
                return new GatewayResponse(false, null, "INSUFFICIENT_FUNDS");
            }
            if (cleanCard.equals("4000000000000115")) {
                return new GatewayResponse(false, null, "EXPIRED_CARD");
            }
            if (cleanCard.equals("4000000000000123")) {
                return new GatewayResponse(false, null, "INCORRECT_CVC");
            }

            // Validate CVV format
            if (cvc != null && !cvc.isBlank() && !cvc.matches("^\\d{3,4}$")) {
                return new GatewayResponse(false, null, "INVALID_CVC");
            }

            // Validate Expiration format
            if (expiry != null && !expiry.isBlank()) {
                if (!expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
                    return new GatewayResponse(false, null, "INVALID_EXPIRY_FORMAT");
                }
                if (isExpired(expiry)) {
                    return new GatewayResponse(false, null, "EXPIRED_CARD");
                }
            }

            // Fallback: regular cards succeed
            return new GatewayResponse(
                    true, "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null);
        }

        // Default rate-based success if no card info or strategy is provided
        if (ThreadLocalRandom.current().nextDouble() < successRate) {
            return new GatewayResponse(
                    true, "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null);
        }
        return new GatewayResponse(false, null, "CARD_DECLINED");
    }

    public GatewayResponse refund(BigDecimal amount) {
        log.info("Mock gateway: amount={}", amount);
        if (ThreadLocalRandom.current().nextDouble() < 0.95) {
            return new GatewayResponse(
                    true, "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null);
        }
        return new GatewayResponse(false, null, "REFUND_GATEWAY_ERROR");
    }

    private boolean checkLuhn(String cardNumber) {
        if (cardNumber == null) return false;
        String digits = cardNumber.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19) return false;
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(digits.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    private boolean isExpired(String expiry) {
        if (expiry == null || !expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) return false;
        String[] parts = expiry.split("/");
        int expMonth = Integer.parseInt(parts[0]);
        int expYear = 2000 + Integer.parseInt(parts[1]);

        java.time.YearMonth expYearMonth = java.time.YearMonth.of(expYear, expMonth);
        return expYearMonth.isBefore(java.time.YearMonth.now());
    }

    public record GatewayResponse(boolean success, String transactionId, String failureReason) {
    }
}
