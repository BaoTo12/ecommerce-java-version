package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {
    List<OrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
