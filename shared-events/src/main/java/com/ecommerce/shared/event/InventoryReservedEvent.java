package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryReservedEvent(
        UUID orderId,
        List<ReservedItem> reservedItems,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant reservedAt
) {
    public record ReservedItem(UUID productId, int quantity) {}
}
