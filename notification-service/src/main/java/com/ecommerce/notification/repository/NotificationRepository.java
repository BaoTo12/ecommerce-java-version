package com.ecommerce.notification.repository;

import com.ecommerce.notification.model.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    Optional<NotificationEntity> findByOrderIdAndType(UUID orderId, String type);
    List<NotificationEntity> findByOrderId(UUID orderId);
}
