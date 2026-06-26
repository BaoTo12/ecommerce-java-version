package com.ecommerce.domain.inventory.adapter.out.persistence;

import com.ecommerce.domain.inventory.domain.model.Inventory;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    private final SpringDataInventoryRepository repository;

    public InventoryPersistenceAdapter(SpringDataInventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Inventory> findById(UUID productId) {
        return repository.findById(productId).map(InventoryPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Inventory> findByIdPessimisticWrite(UUID productId) {
        return repository.findByIdPessimisticWrite(productId).map(InventoryPersistenceMapper::toDomain);
    }

    @Override
    public int atomicDecrement(UUID productId, int amount) {
        return repository.atomicDecrement(productId, amount);
    }

    @Override
    public void atomicIncrement(UUID productId, int amount) {
        repository.atomicIncrement(productId, amount);
    }

    @Override
    public Inventory save(Inventory inventory) {
        InventoryJpaEntity jpa = InventoryPersistenceMapper.toJpa(inventory);
        InventoryJpaEntity saved = repository.save(jpa);
        return InventoryPersistenceMapper.toDomain(saved);
    }

    @Override
    public List<Inventory> findAllById(List<UUID> productIds) {
        return repository.findAllById(productIds).stream()
                .map(InventoryPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }
}
