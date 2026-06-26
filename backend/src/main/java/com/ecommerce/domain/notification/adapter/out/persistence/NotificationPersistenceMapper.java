package com.ecommerce.domain.notification.adapter.out.persistence;

import com.ecommerce.domain.notification.domain.model.NotificationEntity;

public class NotificationPersistenceMapper {

    public static NotificationEntity toDomain(NotificationJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return NotificationEntity.builder()
                .id(jpa.getId())
                .orderId(jpa.getOrderId())
                .userId(jpa.getUserId())
                .type(jpa.getType())
                .recipientEmail(jpa.getRecipientEmail())
                .subject(jpa.getSubject())
                .status(jpa.getStatus())
                .retryCount(jpa.getRetryCount())
                .maxRetry(jpa.getMaxRetry())
                .lastError(jpa.getLastError())
                .sentAt(jpa.getSentAt())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .build();
    }

    public static NotificationJpaEntity toJpa(NotificationEntity domain) {
        if (domain == null) {
            return null;
        }
        return NotificationJpaEntity.builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .userId(domain.getUserId())
                .type(domain.getType())
                .recipientEmail(domain.getRecipientEmail())
                .subject(domain.getSubject())
                .status(domain.getStatus())
                .retryCount(domain.getRetryCount())
                .maxRetry(domain.getMaxRetry())
                .lastError(domain.getLastError())
                .sentAt(domain.getSentAt())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
