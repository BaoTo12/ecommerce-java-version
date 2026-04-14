package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderCreatedEvent(
        UUID orderId,
        UUID userId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    public record OrderItemPayload(UUID productId, int quantity, BigDecimal unitPrice) {}
}
