package com.ecommerce.monolith.domain.coupon.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records each time a user successfully uses a coupon.
 *
 * <p>Edge Case #21: UNIQUE(coupon_id, user_id) prevents the same user from applying the same coupon
 * twice — even under concurrent requests. If two requests from the same user simultaneously try to
 * use the same coupon, only one INSERT succeeds; the second throws DataIntegrityViolationException.
 */
@Entity
@Table(
    name = "coupon_usages",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_coupon_usage_per_user",
          columnNames = {"coupon_id", "user_id"})
    },
    indexes = {@Index(name = "idx_coupon_usage_order", columnList = "order_id")})
public class CouponUsageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "coupon_id", nullable = false)
  private UUID couponId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "discount_applied", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountApplied;

  @Column(name = "used_at", nullable = false, updatable = false)
  private Instant usedAt;

  protected CouponUsageEntity() {}

  public static CouponUsageEntity of(
      UUID couponId, UUID userId, UUID orderId, BigDecimal discount) {
    CouponUsageEntity u = new CouponUsageEntity();
    u.couponId = couponId;
    u.userId = userId;
    u.orderId = orderId;
    u.discountApplied = discount;
    u.usedAt = Instant.now();
    return u;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCouponId() {
    return couponId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public BigDecimal getDiscountApplied() {
    return discountApplied;
  }

  public Instant getUsedAt() {
    return usedAt;
  }
}
