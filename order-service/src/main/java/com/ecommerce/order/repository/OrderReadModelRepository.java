package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.OrderReadModelEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderReadModelRepository extends JpaRepository<OrderReadModelEntity, UUID> {
    Page<OrderReadModelEntity> findByUserId(UUID userId, Pageable pageable);
    Page<OrderReadModelEntity> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);
}
