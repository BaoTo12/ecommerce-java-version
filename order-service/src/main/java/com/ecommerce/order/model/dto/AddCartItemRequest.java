package com.ecommerce.order.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCartItemRequest(
        @NotNull UUID userId,
        @NotNull UUID productId,
        @Min(1) int quantity
) {}
