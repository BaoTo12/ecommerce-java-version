package com.ecommerce.order.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutResponse(UUID checkoutSessionId, UUID orderId, String status, BigDecimal totalAmount) {}
