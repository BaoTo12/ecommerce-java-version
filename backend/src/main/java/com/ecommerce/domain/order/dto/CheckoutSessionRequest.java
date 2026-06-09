package com.ecommerce.domain.order.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;


public record CheckoutSessionRequest(@NotNull UUID cartId, @NotNull BigDecimal totalAmount) {
}
