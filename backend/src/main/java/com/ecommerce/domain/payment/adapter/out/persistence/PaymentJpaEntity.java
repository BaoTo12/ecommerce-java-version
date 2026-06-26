package com.ecommerce.domain.payment.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "payments",
    indexes = {
      @Index(name = "idx_payment_order", columnList = "order_id", unique = true),
      @Index(name = "idx_payment_user", columnList = "user_id")
    })
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Builder.Default
  @Column(nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "refund_reason", length = 500)
  private String refundReason;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Builder.Default
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
