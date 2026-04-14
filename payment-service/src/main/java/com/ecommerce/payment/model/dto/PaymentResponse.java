package com.ecommerce.payment.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID orderId, String status, BigDecimal amount,
                                String gatewayTransactionId, Instant createdAt) {}
