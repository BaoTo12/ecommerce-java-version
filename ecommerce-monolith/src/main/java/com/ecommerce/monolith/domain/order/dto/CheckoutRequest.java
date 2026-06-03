package com.ecommerce.monolith.domain.order.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CheckoutRequest(
    UUID shippingAddressId,
    @Size(max = 500) String notes,
    @Size(max = 64) String idempotencyKey,
    String couponCode) {}
