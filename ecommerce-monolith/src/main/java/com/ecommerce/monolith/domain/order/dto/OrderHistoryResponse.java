package com.ecommerce.monolith.domain.order.dto;

import java.time.Instant;

public record OrderHistoryResponse(
    String fromStatus, String toStatus, String reason, Instant changedAt) {}
