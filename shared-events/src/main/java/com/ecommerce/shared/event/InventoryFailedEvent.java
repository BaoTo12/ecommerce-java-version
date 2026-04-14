package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryFailedEvent(
        UUID orderId,
        UUID productId,
        int requestedQuantity,
        int availableQuantity,
        String reason,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant failedAt
) {}
