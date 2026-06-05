package com.ecommerce.monolith.domain.notification.service;

import com.ecommerce.monolith.domain.notification.entity.NotificationEntity;
import com.ecommerce.monolith.domain.notification.repository.NotificationRepository;
import com.ecommerce.monolith.domain.user.repository.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification Service
 *
 * <p>Edge Case #18 — Notification Deduplication: The OutboxPoller provides at-least-once delivery,
 * meaning the same event can be dispatched more than once (e.g., after an app restart or retry).
 * Without deduplication, a customer receives the same email twice.
 *
 * <p>Two-layer deduplication: Layer 1 (Application): findByOrderIdAndType() checks if already SENT.
 * Layer 2 (Database): UNIQUE(order_id, type) constraint catches race conditions.
 */
@Service
@Transactional
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationRepository notificationRepo;
  private final UserRepository userRepo;
  private final MockEmailSender emailSender;

  public NotificationService(
      NotificationRepository notificationRepo,
      UserRepository userRepo,
      MockEmailSender emailSender) {
    this.notificationRepo = notificationRepo;
    this.userRepo = userRepo;
    this.emailSender = emailSender;
  }

  public void sendOrderConfirmed(UUID orderId, UUID userId) {
    sendNotification(
        orderId,
        userId,
        "ORDER_CONFIRMED",
        "Your order has been confirmed!",
        "Your order #" + orderId + " has been confirmed.");
  }

  public void sendOrderCompleted(UUID orderId, UUID userId) {
    sendNotification(
        orderId,
        userId,
        "ORDER_COMPLETED",
        "Your order is completed!",
        "Your order #" + orderId + " has been completed. Thank you!");
  }

  public void sendOrderCancelled(UUID orderId, UUID userId, String reason) {
    sendNotification(
        orderId,
        userId,
        "ORDER_CANCELLED",
        "Your order has been cancelled",
        "Your order #" + orderId + " was cancelled. Reason: " + reason);
  }

  public void sendPaymentFailed(UUID orderId, UUID userId, String reason) {
    sendNotification(
        orderId,
        userId,
        "PAYMENT_FAILED",
        "Payment failed for your order",
        "Payment for order #" + orderId + " failed: " + reason);
  }

  // Edge Case #18 — Notification Deduplication: 1. Check if notification of this type already
  // exists and was SENT 2. If SENT → return immediately (no duplicate email) 3. If PENDING/FAILED →
  // retry sending 4. DB UNIQUE constraint as final backstop against race conditions
  private void sendNotification(
      UUID orderId, UUID userId, String type, String subject, String body) {
    // ─── Layer 1: Application-level deduplication ─────────────────────
    var existing = notificationRepo.findByOrderIdAndType(orderId, type);
    if (existing.isPresent() && "SENT".equals(existing.get().getStatus())) {
      log.info("Notification already sent: orderId={}, type={}", orderId, type);
      return; // idempotent — no double email
    }
    // ────────────────────────────────────────────────────────────────────

    String recipientEmail =
        userRepo.findById(userId).map(u -> u.getEmail()).orElse("unknown@example.com");

    NotificationEntity notification;
    if (existing.isPresent()) {
      notification = existing.get();
    } else {
      notification =
          NotificationEntity.builder()
              .orderId(orderId)
              .userId(userId)
              .type(type)
              .recipientEmail(recipientEmail)
              .subject(subject)
              .build();
      try {
        // ─── Layer 2: DB UNIQUE constraint catches race conditions ──
        notification = notificationRepo.saveAndFlush(notification);
      } catch (DataIntegrityViolationException e) {
        log.info("Race condition dedup: orderId={}, type={}", orderId, type);
        return; // Another thread just inserted — ours is the duplicate
      }
      // ────────────────────────────────────────────────────────────────
    }

    // Actually send the email
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
}
