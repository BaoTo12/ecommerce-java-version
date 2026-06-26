package com.ecommerce.domain.notification.domain.ports.in;

import java.util.UUID;

public interface NotificationUseCase {
    void sendOrderCompleted(UUID orderId, UUID userId);
    void sendOrderCancelled(UUID orderId, UUID userId, String reason);
    void sendPaymentFailed(UUID orderId, UUID userId, String reason);
    java.util.List<com.ecommerce.domain.notification.domain.model.NotificationEntity> getNotificationsForOrder(UUID orderId);
}
