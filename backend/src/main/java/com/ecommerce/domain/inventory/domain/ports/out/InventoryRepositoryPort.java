package com.ecommerce.domain.inventory.domain.ports.out;

import com.ecommerce.domain.inventory.domain.model.Inventory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepositoryPort {
    Optional<Inventory> findById(UUID productId);
    Optional<Inventory> findByIdPessimisticWrite(UUID productId);
    int atomicDecrement(UUID productId, int amount);
    void atomicIncrement(UUID productId, int amount);
    Inventory save(Inventory inventory);
    List<Inventory> findAllById(List<UUID> productIds);
}
