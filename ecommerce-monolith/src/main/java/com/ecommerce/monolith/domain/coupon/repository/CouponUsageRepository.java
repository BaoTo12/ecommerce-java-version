package com.ecommerce.monolith.domain.coupon.repository;

import com.ecommerce.monolith.domain.coupon.entity.CouponUsageEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsageEntity, UUID> {

  /** Edge Case #21: check per-user usage before allowing apply. */
  int countByCouponIdAndUserId(UUID couponId, UUID userId);

  Optional<CouponUsageEntity> findByOrderId(UUID orderId);
}
