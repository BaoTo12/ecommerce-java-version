package com.ecommerce.monolith.infrastructure.outbox;

import com.ecommerce.monolith.domain.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edge Case #11 — Outbox Pattern: Read/Poll side Edge Case #19 — Graceful Shutdown: @PreDestroy
 * finishes current batch
 *
 * <p>The OutboxPoller runs every 500ms (configurable) and: 1. Fetches a batch of unpublished outbox
 * messages (with SKIP LOCKED) 2. Dispatches each message to the appropriate in-process handler 3.
 * Marks it as published, or increments retry count with exponential backoff
 *
 * <p>In the monolith, "dispatching" means calling a Spring service method directly. No Kafka /
 * message broker needed.
 *
 * <p>Why Outbox still matters in a monolith: If the app crashes AFTER committing the DB transaction
 * but BEFORE making the downstream service call (e.g., sendNotification), that action would be lost
 * without the outbox. The poller guarantees at-least-once delivery.
 */
@Component
public class OutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxRepository repository;
  private final NotificationService notificationService;
  private final ObjectMapper objectMapper;
  private final int batchSize;

  // Edge Case #19: graceful shutdown flag
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  public OutboxPoller(
      OutboxRepository repository,
      NotificationService notificationService,
      ObjectMapper objectMapper,
      @Value("${app.outbox.batch-size:50}") int batchSize) {
    this.repository = repository;
    this.notificationService = notificationService;
    this.objectMapper = objectMapper;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
  @Transactional
  public void poll() {
    if (shuttingDown.get()) return;

    List<OutboxMessageEntity> messages =
        repository.findReadyForProcessing(Instant.now(), batchSize);

    if (messages.isEmpty()) return;

    log.debug("OutboxPoller: processing {} messages", messages.size());

    for (OutboxMessageEntity msg : messages) {
      if (shuttingDown.get()) break;
      try {
        dispatch(msg);
        msg.markPublished();
        log.debug("Outbox message published: id={}, type={}", msg.getId(), msg.getEventType());
      } catch (Exception e) {
        msg.markFailed(e.getMessage());
        if (msg.isExhausted()) {
          log.error(
              "Outbox message exhausted retries: id={}, type={}, lastError={}",
              msg.getId(),
              msg.getEventType(),
              msg.getLastError());
        } else {
          log.warn(
              "Outbox message failed (retry {}): id={}, type={}, error={}",
              msg.getRetryCount(),
              msg.getId(),
              msg.getEventType(),
              e.getMessage());
        }
      }
      repository.save(msg);
    }
  }

  /**
   * Routes an outbox message to the correct handler based on eventType. In a monolith this is a
   * plain method call — no network overhead.
   */
  private void dispatch(OutboxMessageEntity msg) throws Exception {
    JsonNode node = objectMapper.readTree(msg.getPayload());
    switch (msg.getEventType()) {
      case "OrderConfirmedNotification" -> {
        UUID orderId = UUID.fromString(node.get("orderId").asText());
        UUID userId = UUID.fromString(node.get("userId").asText());
        notificationService.sendOrderConfirmed(orderId, userId);
      }
      case "OrderCompletedNotification" -> {
        UUID orderId = UUID.fromString(node.get("orderId").asText());
        UUID userId = UUID.fromString(node.get("userId").asText());
        notificationService.sendOrderCompleted(orderId, userId);
      }
      case "OrderCancelledNotification" -> {
        UUID orderId = UUID.fromString(node.get("orderId").asText());
        UUID userId = UUID.fromString(node.get("userId").asText());
        String reason = node.has("reason") ? node.get("reason").asText() : "Unknown";
        notificationService.sendOrderCancelled(orderId, userId, reason);
      }
      case "PaymentFailedNotification" -> {
        UUID orderId = UUID.fromString(node.get("orderId").asText());
        UUID userId = UUID.fromString(node.get("userId").asText());
        String reason = node.has("failureReason") ? node.get("failureReason").asText() : "Unknown";
        notificationService.sendPaymentFailed(orderId, userId, reason);
      }
      default -> log.warn("No handler for outbox event type: {}", msg.getEventType());
    }
  }

  /**
   * Edge Case #19 — Graceful Shutdown: Signal the poller to stop before destroying the bean.
   * Spring's graceful shutdown waits for in-flight requests, but the poller is a scheduled task, so
   * we need this additional guard.
   */
  @PreDestroy
  public void onShutdown() {
    log.info("OutboxPoller: shutdown signal received, finishing current batch...");
    shuttingDown.set(true);
  }
}
