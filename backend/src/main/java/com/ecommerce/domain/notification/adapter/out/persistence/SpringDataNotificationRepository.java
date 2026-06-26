package com.ecommerce.domain.notification.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    Optional<NotificationJpaEntity> findByOrderIdAndType(UUID orderId, String type);

    List<NotificationJpaEntity> findByOrderId(UUID orderId);
}
