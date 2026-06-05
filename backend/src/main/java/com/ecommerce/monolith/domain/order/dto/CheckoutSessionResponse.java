package com.ecommerce.monolith.domain.order.dto;

import com.ecommerce.monolith.common.status.CheckoutSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckoutSessionResponse(
        UUID idempotencyKey,
        CheckoutSessionStatus status,
        UUID cartId,
        Instant expiresAt,
        BigDecimal totalAmount,
        Instant createdAt) {
}
