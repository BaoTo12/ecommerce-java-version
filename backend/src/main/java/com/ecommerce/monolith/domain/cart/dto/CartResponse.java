package com.ecommerce.monolith.domain.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
    UUID cartId, UUID userId, List<CartItemDto> items, BigDecimal totalAmount, Instant updatedAt) {
  public static CartResponse empty(UUID userId) {
    return new CartResponse(null, userId, List.of(), BigDecimal.ZERO, Instant.now());
  }

  public record CartItemDto(
      UUID itemId,
      UUID productId,
      String productName,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal,
      boolean selected) {}
}
