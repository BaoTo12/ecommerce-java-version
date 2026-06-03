package com.ecommerce.monolith.domain.coupon.service;

import com.ecommerce.monolith.domain.coupon.dto.ApplyCouponRequest;
import com.ecommerce.monolith.domain.coupon.dto.CouponDiscountResult;
import com.ecommerce.monolith.domain.coupon.entity.CouponEntity;
import com.ecommerce.monolith.domain.coupon.entity.CouponUsageEntity;
import com.ecommerce.monolith.domain.coupon.repository.CouponRepository;
import com.ecommerce.monolith.domain.coupon.repository.CouponUsageRepository;
import com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.infrastructure.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupon Service
 *
 * <p>Edge Case #21 — Coupon Race Condition (Multi-layer defense):
 *
 * <p>Problem: "First 100 uses" coupons. 500 users simultaneously apply the coupon. Without
 * protection all 500 read usedCount=99, pass the check, succeed → 500 uses.
 *
 * <p>Layer 1 — Pre-flight check: quick rejection of obviously invalid coupons (expired, inactive).
 * Reduces DB load but does NOT prevent race conditions.
 *
 * <p>Layer 2 — Atomic SQL increment: UPDATE ... WHERE used_count < max_uses. DB atomically checks
 * AND increments. Returns 0 if coupon is full. This is the real race-condition protection — same
 * pattern as Edge Case #14.
 *
 * <p>Layer 3 — UNIQUE(coupon_id, user_id) in coupon_usages: Even if the same user submits two
 * concurrent requests that both pass Layer 2, only one CouponUsage INSERT succeeds. The second
 * throws DataIntegrityViolationException → caught → rollback the increment.
 *
 * <p>Edge Case #22 — Money Precision: All discount calculations use BigDecimal with explicit
 * HALF_UP rounding via MoneyUtils.
 */
@Service
@Transactional
public class CouponService {

  private static final Logger log = LoggerFactory.getLogger(CouponService.class);

  private final CouponRepository couponRepo;
  private final CouponUsageRepository usageRepo;

  public CouponService(CouponRepository couponRepo, CouponUsageRepository usageRepo) {
    this.couponRepo = couponRepo;
    this.usageRepo = usageRepo;
  }

  /**
   * Validates and applies a coupon to an order. Returns the discount amount to subtract from the
   * total.
   *
   * <p>Must be called WITHIN the checkout transaction so that rollback releases the coupon slot if
   * a later step fails.
   */
  public CouponDiscountResult applyCoupon(ApplyCouponRequest req, UUID userId, UUID orderId) {
    // ─── Layer 1: Pre-flight (fast, no DB write) ─────────────────────────
    CouponEntity coupon =
        couponRepo
            .findByCodeIgnoreCase(req.couponCode())
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "Coupon code not found: " + req.couponCode()));

    if (!coupon.isValid()) {
      throw new BusinessRuleViolationException(
          "Coupon '" + req.couponCode() + "' is expired or no longer available.");
    }

    if (coupon.getMinOrderAmount() != null
        && req.orderTotal().compareTo(coupon.getMinOrderAmount()) < 0) {
      throw new BusinessRuleViolationException(
          "Order total must be at least " + coupon.getMinOrderAmount() + " to use this coupon.");
    }

    // Per-user limit check
    int userUseCount = usageRepo.countByCouponIdAndUserId(coupon.getId(), userId);
    if (userUseCount >= coupon.getMaxUsesPerUser()) {
      throw new BusinessRuleViolationException(
          "You have already used this coupon the maximum number of times.");
    }
    // ─────────────────────────────────────────────────────────────────────

    // ─── Layer 2: Atomic increment ────────────────────────────────────────
    // This is the definitive race-condition guard. If the coupon just hit
    // max_uses between the pre-flight check and here, this returns 0.
    int rowsUpdated = couponRepo.atomicIncrementUsage(coupon.getId());
    if (rowsUpdated == 0) {
      throw new BusinessRuleViolationException(
          "Coupon '" + req.couponCode() + "' has reached its usage limit.");
    }
    // ─────────────────────────────────────────────────────────────────────

    // ─── Edge Case #22: Money Precision ──────────────────────────────────
    BigDecimal discount = calculateDiscount(coupon, req.orderTotal());
    // ─────────────────────────────────────────────────────────────────────

    // ─── Layer 3: Record usage with UNIQUE constraint guard ───────────────
    try {
      CouponUsageEntity usage = CouponUsageEntity.of(coupon.getId(), userId, orderId, discount);
      usageRepo.saveAndFlush(usage);
    } catch (DataIntegrityViolationException e) {
      // Same user applied the same coupon in a concurrent request.
      // Rollback the increment we just did.
      log.warn("Concurrent coupon apply detected: userId={}, coupon={}", userId, req.couponCode());
      // The transaction rollback will undo the atomicIncrement too (it's in the same TX)
      throw new BusinessRuleViolationException("You have already used this coupon.");
    }
    // ─────────────────────────────────────────────────────────────────────

    log.info("Coupon applied: code={}, userId={}, discount={}", req.couponCode(), userId, discount);
    return new CouponDiscountResult(
        coupon.getId(), req.couponCode(), discount, req.orderTotal().subtract(discount));
  }

  /**
   * Validates a coupon code for a given order total and user, without applying it. Useful for
   * checking coupon validity on the frontend before checkout.
   */
  @Transactional(readOnly = true)
  public CouponDiscountResult validateCoupon(String code, UUID userId, BigDecimal orderTotal) {
    CouponEntity coupon =
        couponRepo
            .findByCodeIgnoreCase(code)
            .orElseThrow(
                () ->
                    new com.ecommerce.monolith.infrastructure.exception
                        .BusinessRuleViolationException("Coupon code not found: " + code));

    if (!coupon.isValid()) {
      throw new com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException(
          "Coupon '" + code + "' is expired or no longer available.");
    }

    if (coupon.getMinOrderAmount() != null
        && orderTotal.compareTo(coupon.getMinOrderAmount()) < 0) {
      throw new com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException(
          "Order total must be at least " + coupon.getMinOrderAmount() + " to use this coupon.");
    }

    // Per-user limit check
    int userUseCount = usageRepo.countByCouponIdAndUserId(coupon.getId(), userId);
    if (userUseCount >= coupon.getMaxUsesPerUser()) {
      throw new com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException(
          "You have already used this coupon.");
    }

    // Usage limit check
    if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) {
      throw new com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException(
          "Coupon '" + code + "' has reached its usage limit.");
    }

    BigDecimal discount = calculateDiscount(coupon, orderTotal);
    return new CouponDiscountResult(coupon.getId(), code, discount, orderTotal.subtract(discount));
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  /**
   * Edge Case #22 — Money Precision: All arithmetic uses BigDecimal. Percentage discounts round
   * HALF_UP to 2 decimal places. maxDiscountAmount caps PERCENTAGE coupons (e.g., "20% off, max $50
   * discount").
   */
  private BigDecimal calculateDiscount(CouponEntity coupon, BigDecimal orderTotal) {
    BigDecimal discount;

    if ("PERCENTAGE".equals(coupon.getDiscountType())) {
      // e.g., 20% of 123.456 → raw = 24.6912 → rounded = 24.69
      BigDecimal raw =
          orderTotal
              .multiply(coupon.getDiscountValue())
              .divide(MoneyUtils.HUNDRED, 4, RoundingMode.HALF_UP);
      discount = MoneyUtils.round(raw);

      // Cap the discount at maxDiscountAmount
      if (coupon.getMaxDiscountAmount() != null
          && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
        discount = coupon.getMaxDiscountAmount();
      }
    } else {
      // FIXED_AMOUNT: cannot discount more than the order total
      discount = coupon.getDiscountValue().min(orderTotal);
    }

    return MoneyUtils.round(discount);
  }
}
