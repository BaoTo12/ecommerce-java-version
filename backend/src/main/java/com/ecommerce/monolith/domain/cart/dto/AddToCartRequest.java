package com.ecommerce.monolith.domain.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddToCartRequest(@NotNull UUID productId, @Min(1) int quantity) {}
