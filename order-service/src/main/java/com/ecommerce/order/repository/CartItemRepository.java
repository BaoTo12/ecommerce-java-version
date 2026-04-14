package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {}
