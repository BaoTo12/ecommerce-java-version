package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.entity.InventoryReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, UUID> {
    List<InventoryReservationEntity> findByOrderIdAndOperation(UUID orderId, String operation);
}
