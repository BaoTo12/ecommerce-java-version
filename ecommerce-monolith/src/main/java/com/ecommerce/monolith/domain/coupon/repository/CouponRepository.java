package com.ecommerce.monolith.domain.coupon.repository;

import com.ecommerce.monolith.domain.coupon.entity.CouponEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CouponRepository extends JpaRepository<CouponEntity, UUID> {

  Optional<CouponEntity> findByCodeIgnoreCase(String code);

  /**
   * Edge Case #21 — Atomic Coupon Increment:
   *
   * <p>Same principle as Edge Case #14 (atomic inventory decrement). The WHERE clause atomically
   * checks max_uses before incrementing. Returns 1 = success, 0 = coupon fully used up.
   *
   * <p>Why not just load the entity and increment in Java? Race condition: Thread A and B both read
   * used_count=99 (max_uses=100), both pass the check, both save → used_count=100 from two separate
   * "101st" uses.
   *
   * <p>This single SQL atomically does the check AND the increment. The database serializes the row
   * lock — only one thread wins per "slot."
   */
  @Modifying
  @Query(
      value =
          """
        UPDATE coupons
        SET used_count = used_count + 1
        WHERE id = :couponId
          AND is_active = true
          AND (max_uses IS NULL OR used_count < max_uses)
          AND valid_from <= NOW()
          AND valid_until > NOW()
        """,
      nativeQuery = true)
  int atomicIncrementUsage(UUID couponId);
}
