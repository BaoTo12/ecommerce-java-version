package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentRefundedEvent(
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant refundedAt
) {}
