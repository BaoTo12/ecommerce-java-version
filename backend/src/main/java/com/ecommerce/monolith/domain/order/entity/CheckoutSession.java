package com.ecommerce.monolith.domain.order.entity;

import com.ecommerce.monolith.common.status.CheckoutSessionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_sessions")
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
public class CheckoutSession {
    @Id
    UUID idempotencyKey;

    UUID userId;
    UUID cartId;

    BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    CheckoutSessionStatus status;

    String responseBody;

    Instant createdAt;

    @PrePersist
    void setCreatedAt(){
        createdAt = Instant.now();
    }
}
