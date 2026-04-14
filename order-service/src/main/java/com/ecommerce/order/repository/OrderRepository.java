package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Page<OrderEntity> findByUserId(UUID userId, Pageable pageable);
    Page<OrderEntity> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);
}
