package com.ecommerce.order.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID userId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotNull UUID productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
