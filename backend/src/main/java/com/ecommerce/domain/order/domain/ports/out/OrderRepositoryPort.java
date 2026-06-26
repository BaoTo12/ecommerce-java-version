package com.ecommerce.domain.order.domain.ports.out;

import com.ecommerce.domain.order.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {
    Page<UUID> findOrderIdsByUserId(UUID userId, Pageable pageable);
    List<Order> findByIdsWithItems(List<UUID> ids);
    Optional<Order> findByIdWithItems(UUID id);
    Page<Order> findByUserId(UUID userId, Pageable pageable);
    Optional<Order> findById(UUID id);
    Order save(Order order);
}
