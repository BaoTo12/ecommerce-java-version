package com.ecommerce.domain.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentDetailsDto(
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String cardNumber,
        String cvc,
        String cardName,
        String expiry,
        String strategy) {
}
