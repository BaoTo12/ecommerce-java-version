package com.ecommerce.domain.order.domain.model;

import com.ecommerce.common.status.CheckoutSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSession {
    private UUID idempotencyKey;
    private UUID userId;
    private UUID cartId;
    private UUID orderId;
    private BigDecimal totalAmount;
    private CheckoutSessionStatus status;
    private String responseBody;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    private Instant expiresAt;
}
