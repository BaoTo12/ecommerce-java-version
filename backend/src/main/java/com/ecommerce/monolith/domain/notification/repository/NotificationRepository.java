package com.ecommerce.monolith.domain.notification.repository;

import com.ecommerce.monolith.domain.notification.entity.NotificationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    // Edge Case #18 — Deduplication: Check if a notification of this type was already sent for the order
    Optional<NotificationEntity> findByOrderIdAndType(UUID orderId, String type);

    List<NotificationEntity> findByOrderId(UUID orderId);
}
