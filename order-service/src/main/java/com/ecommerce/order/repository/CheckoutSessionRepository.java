package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.CheckoutSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSessionEntity, UUID> {}
