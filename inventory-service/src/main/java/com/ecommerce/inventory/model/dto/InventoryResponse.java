package com.ecommerce.inventory.model.dto;

import java.util.UUID;

public record InventoryResponse(UUID productId, int quantity, long version) {}
