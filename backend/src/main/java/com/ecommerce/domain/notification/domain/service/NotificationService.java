package com.ecommerce.domain.notification.domain.service;

import com.ecommerce.domain.notification.domain.model.DuplicateNotificationException;
import com.ecommerce.domain.notification.domain.model.NotificationEntity;
import com.ecommerce.domain.notification.domain.ports.in.NotificationUseCase;
import com.ecommerce.domain.notification.domain.ports.out.EmailSenderPort;
import com.ecommerce.domain.notification.domain.ports.out.NotificationRepositoryPort;
import com.ecommerce.domain.notification.domain.ports.out.NotificationUserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class NotificationService implements NotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepositoryPort notificationRepo;
    private final NotificationUserRepositoryPort userRepo;
    private final EmailSenderPort emailSender;

    public NotificationService(
            NotificationRepositoryPort notificationRepo,
            NotificationUserRepositoryPort userRepo,
            EmailSenderPort emailSender) {
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
        this.emailSender = emailSender;
    }

    @Override
    public void sendOrderCompleted(UUID orderId, UUID userId) {
        sendNotification(
                orderId,
                userId,
                "ORDER_COMPLETED",
                "Your order is completed!",
                "Your order #" + orderId + " has been completed. Thank you!");
    }

    @Override
    public void sendOrderCancelled(UUID orderId, UUID userId, String reason) {
        sendNotification(
                orderId,
                userId,
                "ORDER_CANCELLED",
                "Your order has been cancelled",
                "Your order #" + orderId + " was cancelled. Reason: " + reason);
    }

    @Override
    public void sendPaymentFailed(UUID orderId, UUID userId, String reason) {
        sendNotification(
                orderId,
                userId,
                "PAYMENT_FAILED",
                "Payment failed for your order",
                "Payment for order #" + orderId + " failed: " + reason);
    }

    private void sendNotification(
            UUID orderId, UUID userId, String type, String subject, String body) {
        var existing = notificationRepo.findByOrderIdAndType(orderId, type);
        if (existing.isPresent() && "SENT".equals(existing.get().getStatus())) {
            log.info("Notification already sent: orderId={}, type={}", orderId, type);
            return;
        }

        String recipientEmail = userRepo.findEmailByUserId(userId).orElse("unknown@example.com");

        NotificationEntity notification;
        if (existing.isPresent()) {
            notification = existing.get();
        } else {
            notification = NotificationEntity.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .type(type)
                    .recipientEmail(recipientEmail)
                    .subject(subject)
                    .build();
            try {
                notification = notificationRepo.saveAndFlush(notification);
            } catch (DuplicateNotificationException e) {
                log.info("Race condition dedup: orderId={}, type={}", orderId, type);
                return;
            }
        }

        try {
            emailSender.send(recipientEmail, subject, body);
            notification.markSent();
            notificationRepo.save(notification);
            log.info("Notification sent: type={}, orderId={}, to={}", type, orderId, recipientEmail);
        } catch (Exception e) {
            notification.markFailed(e.getMessage());
            notificationRepo.save(notification);
            log.error("Notification failed: type={}, orderId={}", type, orderId, e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    @Override
    public java.util.List<NotificationEntity> getNotificationsForOrder(UUID orderId) {
        return notificationRepo.findByOrderId(orderId);
    }
}
