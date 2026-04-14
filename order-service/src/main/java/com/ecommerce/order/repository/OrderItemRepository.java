package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {}
