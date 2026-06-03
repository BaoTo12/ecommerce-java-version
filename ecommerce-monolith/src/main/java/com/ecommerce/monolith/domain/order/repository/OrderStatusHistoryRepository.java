package com.ecommerce.monolith.domain.order.repository;

import com.ecommerce.monolith.domain.order.entity.OrderStatusHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository
    extends JpaRepository<OrderStatusHistoryEntity, UUID> {
  List<OrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
