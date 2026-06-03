package com.ecommerce.monolith.domain.inventory.repository;

import com.ecommerce.monolith.domain.inventory.entity.InventoryReservationEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryReservationRepository
    extends JpaRepository<InventoryReservationEntity, UUID> {

  List<InventoryReservationEntity> findByOrderId(UUID orderId);

  /**
   * Edge Case #5 — Reservation TTL: Finds all active reservations whose TTL has passed. Called by
   * InventoryExpiryJob every N minutes.
   */
  @Query(
      """
        SELECT r FROM InventoryReservationEntity r
        WHERE r.operation = 'RESERVE'
          AND r.status = 'SUCCESS'
          AND r.releasedAt IS NULL
          AND r.expiresAt IS NOT NULL
          AND r.expiresAt < :now
        """)
  List<InventoryReservationEntity> findExpiredReservations(Instant now);
}
