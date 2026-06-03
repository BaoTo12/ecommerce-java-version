package com.ecommerce.monolith.domain.coupon.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Coupon entity.
 *
 * <p>Edge Case #21 — Coupon Race Condition: "First 100 customers get 20% off" coupons are
 * notoriously hard. Without atomic tracking, 150 customers can all read usedCount=99 concurrently,
 * all pass the "< maxUses" check, and all successfully apply the coupon → 150 uses on a 100-use
 * coupon.
 *
 * <p>This entity is the source of truth for coupon validity and usage.
 */
@Entity
@Table(
    name = "coupons",
    indexes = {@Index(name = "idx_coupon_code", columnList = "code", unique = true)})
public class CouponEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  /** PERCENTAGE (e.g. 20 = 20% off) or FIXED_AMOUNT (e.g. 50000 VND off) */
  @Column(name = "discount_type", nullable = false, length = 20)
  private String discountType;

  @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
  private BigDecimal discountValue;

  @Column(name = "min_order_amount", precision = 15, scale = 2)
  private BigDecimal minOrderAmount;

  @Column(name = "max_discount_amount", precision = 15, scale = 2)
  private BigDecimal maxDiscountAmount;

  /** Edge Case #21: NULL = unlimited uses. Integer = max allowed uses across all users. */
  @Column(name = "max_uses")
  private Integer maxUses;

  /**
   * Edge Case #21: Atomically incremented. Never decremented (no taking back used coupons).
   * Compared against maxUses with a WHERE clause to prevent overshooting.
   */
  @Column(name = "used_count", nullable = false)
  private int usedCount = 0;

  /** Edge Case #21: Per-user limit. Enforced by UNIQUE(coupon_id, user_id) in coupon_usages. */
  @Column(name = "max_uses_per_user", nullable = false)
  private int maxUsesPerUser = 1;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_until", nullable = false)
  private Instant validUntil;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CouponEntity() {}

  public boolean isValid() {
    Instant now = Instant.now();
    return isActive
        && now.isAfter(validFrom)
        && now.isBefore(validUntil)
        && (maxUses == null || usedCount < maxUses);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getDiscountType() {
    return discountType;
  }

  public BigDecimal getDiscountValue() {
    return discountValue;
  }

  public BigDecimal getMinOrderAmount() {
    return minOrderAmount;
  }

  public BigDecimal getMaxDiscountAmount() {
    return maxDiscountAmount;
  }

  public Integer getMaxUses() {
    return maxUses;
  }

  public int getUsedCount() {
    return usedCount;
  }

  public int getMaxUsesPerUser() {
    return maxUsesPerUser;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public boolean isActive() {
    return isActive;
  }
}
