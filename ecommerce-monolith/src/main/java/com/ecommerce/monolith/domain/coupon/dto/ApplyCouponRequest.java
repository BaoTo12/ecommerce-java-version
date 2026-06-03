package com.ecommerce.monolith.domain.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ApplyCouponRequest(@NotBlank String couponCode, BigDecimal orderTotal) {}
