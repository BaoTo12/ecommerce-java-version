package com.ecommerce.notification.service;

import com.ecommerce.notification.integration.email.MockEmailSender;
import com.ecommerce.notification.model.entity.NotificationEntity;
import com.ecommerce.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifRepo;
    private final MockEmailSender emailSender;

    public NotificationService(NotificationRepository notifRepo, MockEmailSender emailSender) {
        this.notifRepo = notifRepo;
        this.emailSender = emailSender;
    }

    public void sendOrderConfirmed(UUID orderId, UUID userId) {
        sendNotification(orderId, userId, "ORDER_CONFIRMED",
                "Your order has been confirmed!",
                "Hi! Your order %s has been confirmed and is being processed.".formatted(orderId));
    }

    public void sendOrderCompleted(UUID orderId, UUID userId) {
        sendNotification(orderId, userId, "ORDER_COMPLETED",
                "Your order is complete!",
                "Hi! Your order %s has been completed successfully. Thank you for your purchase!".formatted(orderId));
    }

    public void sendOrderCancelled(UUID orderId, UUID userId, String reason) {
        sendNotification(orderId, userId, "ORDER_CANCELLED",
                "Your order has been cancelled",
                "Hi! Your order %s has been cancelled. Reason: %s".formatted(orderId, reason));
    }

    public void sendPaymentFailed(UUID orderId, UUID userId, String reason) {
        sendNotification(orderId, userId, "PAYMENT_FAILED",
                "Payment failed for your order",
                "Hi! Payment for your order %s has failed. Reason: %s".formatted(orderId, reason));
    }

    private void sendNotification(UUID orderId, UUID userId, String type, String subject, String body) {
        // Check if already sent (idempotency at DB level via UNIQUE constraint)
        var existing = notifRepo.findByOrderIdAndType(orderId, type);
        if (existing.isPresent() && "SENT".equals(existing.get().getStatus())) {
            log.warn("Notification already sent: orderId={}, type={}", orderId, type);
            return;
        }

        NotificationEntity notif = existing.orElseGet(() ->
                notifRepo.save(NotificationEntity.create(orderId, userId, type, subject)));

        try {
            boolean sent = emailSender.send(notif.getRecipientEmail(), subject, body);
            if (sent) {
                notif.markSent();
                notifRepo.save(notif);
                log.info("Notification sent: orderId={}, type={}", orderId, type);
            }
        } catch (Exception e) {
            notif.markFailed(e.getMessage());
            notifRepo.save(notif);
            log.error("Notification failed: orderId={}, type={}", orderId, type, e);
        }
    }
}
