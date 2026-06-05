package com.ecommerce.monolith.domain.order.entity;

import com.ecommerce.monolith.common.status.CheckoutSessionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "checkout_sessions")
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@Getter
@Setter
public class CheckoutSession {
  @Id UUID idempotencyKey;

  UUID userId;
  UUID cartId;

  @Column(name = "order_id")
  UUID orderId;

  BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  CheckoutSessionStatus status;

  String responseBody;
  Instant createdAt;
  Instant expiresAt;

  @PrePersist
  void setCreatedAt() {
    createdAt = Instant.now();
  }
}
