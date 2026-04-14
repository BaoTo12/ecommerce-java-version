package com.ecommerce.order.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(UUID cartId, UUID userId, String status, List<CartItemDto> items, BigDecimal subtotal) {
    public record CartItemDto(UUID itemId, UUID productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
