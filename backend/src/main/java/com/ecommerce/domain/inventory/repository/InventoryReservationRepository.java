package com.ecommerce.domain.inventory.repository;

import com.ecommerce.domain.inventory.entity.InventoryReservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

  List<InventoryReservation> findByOrderId(UUID orderId);

  @Query(
      """
        SELECT r FROM InventoryReservation r
        WHERE r.operation = 'RESERVE'
          AND r.status = 'SUCCESS'
          AND r.releasedAt IS NULL
          AND r.expiresAt IS NOT NULL
          AND r.expiresAt < :now
        """)
  List<InventoryReservation> findExpiredReservations(Instant now);

  @Query(
      """
        SELECT r FROM InventoryReservation r
        WHERE r.productId IN :productIds
          AND r.status = 'HOLDING'
          AND r.createdAt BETWEEN :start AND :end
      """)
  List<InventoryReservation> findHoldingReservations(
      @Param("productIds") List<UUID> productIds,
      @Param("start") Instant start,
      @Param("end") Instant end);

  @Query("SELECT r FROM InventoryReservation r WHERE r.status = 'HOLDING' AND r.expiresAt < :now")
  List<InventoryReservation> findExpiredHoldingReservations(@Param("now") Instant now);
}
