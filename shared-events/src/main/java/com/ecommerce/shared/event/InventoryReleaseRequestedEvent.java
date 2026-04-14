package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryReleaseRequestedEvent(
        UUID orderId,
        List<OrderCreatedEvent.OrderItemPayload> items,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant requestedAt
) {}
