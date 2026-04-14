package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<InventoryEntity, UUID> {}
