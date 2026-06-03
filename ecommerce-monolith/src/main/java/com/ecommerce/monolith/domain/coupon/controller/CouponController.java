package com.ecommerce.monolith.domain.coupon.controller;

import com.ecommerce.monolith.domain.coupon.dto.ApplyCouponRequest;
import com.ecommerce.monolith.domain.coupon.dto.CouponDiscountResult;
import com.ecommerce.monolith.domain.coupon.service.CouponService;
import com.ecommerce.monolith.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/coupons")
public class CouponController {

  private final CouponService couponService;

  public CouponController(CouponService couponService) {
    this.couponService = couponService;
  }

  @PostMapping("/validate")
  public CouponDiscountResult validateCoupon(@Valid @RequestBody ApplyCouponRequest req) {
    UUID userId = SecurityUtils.getCurrentUserId();
    return couponService.validateCoupon(req.couponCode(), userId, req.orderTotal());
  }
}
