package com.ecommerce.domain.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID orderId,
        String status,
        BigDecimal amount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {
}
