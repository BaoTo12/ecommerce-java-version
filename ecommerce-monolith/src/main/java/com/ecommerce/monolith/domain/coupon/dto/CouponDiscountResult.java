package com.ecommerce.monolith.domain.coupon.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponDiscountResult(
    UUID couponId, String code, BigDecimal discountAmount, BigDecimal finalAmount) {}
