package com.ecommerce.monolith.domain.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID orderId,
    String status,
    BigDecimal amount,
    String gatewayTransactionId,
    String failureReason,
    Instant createdAt,
    Instant updatedAt) {}
