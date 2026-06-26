package com.ecommerce.domain.payment.domain.ports.out;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayPort {
    GatewayResponse charge(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String cardNumber,
            String cvc,
            String cardName,
            String expiry,
            String strategy);

    GatewayResponse refund(BigDecimal amount);

    record GatewayResponse(boolean success, String transactionId, String failureReason) {}
}
