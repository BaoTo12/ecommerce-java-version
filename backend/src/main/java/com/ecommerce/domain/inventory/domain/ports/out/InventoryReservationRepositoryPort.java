package com.ecommerce.domain.inventory.domain.ports.out;

import com.ecommerce.domain.inventory.domain.model.InventoryReservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface InventoryReservationRepositoryPort {
    List<InventoryReservation> findByOrderId(UUID orderId);
    List<InventoryReservation> findExpiredReservations(Instant now);
    List<InventoryReservation> findHoldingReservations(List<UUID> productIds, Instant start, Instant end);
    List<InventoryReservation> findExpiredHoldingReservations(Instant now);
    InventoryReservation save(InventoryReservation reservation);
    List<InventoryReservation> saveAll(List<InventoryReservation> reservations);
    Optional<InventoryReservation> findById(UUID id);
}
