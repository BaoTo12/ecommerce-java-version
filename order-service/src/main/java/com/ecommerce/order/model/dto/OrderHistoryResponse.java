package com.ecommerce.order.model.dto;

import java.time.Instant;

public record OrderHistoryResponse(String fromStatus, String toStatus, String reason, Instant timestamp) {}
