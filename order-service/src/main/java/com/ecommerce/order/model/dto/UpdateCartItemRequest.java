package com.ecommerce.order.model.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(@Min(1) int quantity) {}
