package com.ecommerce.domain.inventory.adapter.out.persistence;

import com.ecommerce.domain.inventory.domain.model.InventoryReservation;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryReservationRepositoryPort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class InventoryReservationPersistenceAdapter implements InventoryReservationRepositoryPort {

    private final SpringDataInventoryReservationRepository repository;

    public InventoryReservationPersistenceAdapter(SpringDataInventoryReservationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<InventoryReservation> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).stream()
                .map(InventoryPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<InventoryReservation> findExpiredReservations(Instant now) {
        return repository.findExpiredReservations(now).stream()
                .map(InventoryPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<InventoryReservation> findHoldingReservations(List<UUID> productIds, Instant start, Instant end) {
        return repository.findHoldingReservations(productIds, start, end).stream()
                .map(InventoryPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<InventoryReservation> findExpiredHoldingReservations(Instant now) {
        return repository.findExpiredHoldingReservations(now).stream()
                .map(InventoryPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public InventoryReservation save(InventoryReservation reservation) {
        InventoryReservationJpaEntity jpa = InventoryPersistenceMapper.toJpa(reservation);
        InventoryReservationJpaEntity saved = repository.save(jpa);
        return InventoryPersistenceMapper.toDomain(saved);
    }

    @Override
    public List<InventoryReservation> saveAll(List<InventoryReservation> reservations) {
        List<InventoryReservationJpaEntity> jpas = reservations.stream()
                .map(InventoryPersistenceMapper::toJpa)
                .collect(Collectors.toList());
        List<InventoryReservationJpaEntity> saved = repository.saveAll(jpas);
        return saved.stream()
                .map(InventoryPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<InventoryReservation> findById(UUID id) {
        return repository.findById(id).map(InventoryPersistenceMapper::toDomain);
    }
}
