package com.ecommerce.monolith.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CheckoutRequest(
      @NotNull UUID cartId,
      @NotNull UUID order
) {
}
