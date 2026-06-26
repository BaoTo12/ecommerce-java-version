package com.ecommerce.domain.notification.domain.ports.out;

import com.ecommerce.domain.notification.domain.model.NotificationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {
    Optional<NotificationEntity> findByOrderIdAndType(UUID orderId, String type);
    List<NotificationEntity> findByOrderId(UUID orderId);
    NotificationEntity save(NotificationEntity notification);
    NotificationEntity saveAndFlush(NotificationEntity notification);
}
