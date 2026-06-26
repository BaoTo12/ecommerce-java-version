package com.ecommerce.domain.order.adapter.out.persistence;

import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.order.domain.ports.out.OrderRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository repository;

    public OrderPersistenceAdapter(SpringDataOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<UUID> findOrderIdsByUserId(UUID userId, Pageable pageable) {
        return repository.findOrderIdsByUserId(userId, pageable);
    }

    @Override
    public List<Order> findByIdsWithItems(List<UUID> ids) {
        return repository.findByIdsWithItems(ids).stream()
                .map(OrderPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findByIdWithItems(UUID id) {
        return repository.findByIdWithItems(id).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    public Page<Order> findByUserId(UUID userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return repository.findById(id).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity jpa = OrderPersistenceMapper.toJpa(order);
        OrderJpaEntity saved = repository.save(jpa);
        return OrderPersistenceMapper.toDomain(saved);
    }
}
