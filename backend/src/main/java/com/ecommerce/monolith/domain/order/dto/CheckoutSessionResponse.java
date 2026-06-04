package com.ecommerce.monolith.domain.order.dto;

import com.ecommerce.monolith.common.status.CheckoutSessionStatus;
import com.ecommerce.monolith.common.util.Money;

import java.time.Instant;
import java.util.UUID;

public record CheckoutSessionResponse(
        UUID idempotencyKey,
        CheckoutSessionStatus status,
        UUID cartId,
        Money totalAmount,
        Instant createdAt) {}
