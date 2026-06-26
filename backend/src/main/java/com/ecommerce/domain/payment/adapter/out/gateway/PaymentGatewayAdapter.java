package com.ecommerce.domain.payment.adapter.out.gateway;

import com.ecommerce.domain.payment.domain.ports.out.PaymentGatewayPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentGatewayAdapter implements PaymentGatewayPort {

    private final MockPaymentGatewayClient gatewayClient;

    public PaymentGatewayAdapter(MockPaymentGatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public GatewayResponse charge(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String cardNumber,
            String cvc,
            String cardName,
            String expiry,
            String strategy) {
        MockPaymentGatewayClient.GatewayResponse response = gatewayClient.charge(
                orderId, userId, amount, cardNumber, cvc, cardName, expiry, strategy);
        return new GatewayResponse(response.success(), response.transactionId(), response.failureReason());
    }

    @Override
    public GatewayResponse refund(BigDecimal amount) {
        MockPaymentGatewayClient.GatewayResponse response = gatewayClient.refund(amount);
        return new GatewayResponse(response.success(), response.transactionId(), response.failureReason());
    }
}
