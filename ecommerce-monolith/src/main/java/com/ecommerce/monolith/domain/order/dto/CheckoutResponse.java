package com.ecommerce.monolith.domain.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutResponse(
    UUID orderId, String status, BigDecimal totalAmount, String message) {}
