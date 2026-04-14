package com.ecommerce.order.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id, UUID userId, String status, BigDecimal totalAmount,
        List<OrderItemDto> items, Instant createdAt, Instant updatedAt
) {
    public record OrderItemDto(UUID productId, int quantity, BigDecimal unitPrice) {}
}
