package com.ecommerce.payment.repository;

import com.ecommerce.payment.model.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByOrderId(UUID orderId);
    boolean existsByOrderId(UUID orderId);
}
