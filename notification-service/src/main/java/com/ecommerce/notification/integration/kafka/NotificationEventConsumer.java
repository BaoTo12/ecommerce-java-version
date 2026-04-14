package com.ecommerce.notification.integration.kafka;

import com.ecommerce.notification.service.NotificationService;
import com.ecommerce.shared.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private final NotificationService notifService;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationService notifService, ObjectMapper objectMapper) {
        this.notifService = notifService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_CREATED, groupId = "notification-service")
    public void onOrderCreated(String message) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
            MDC.put("orderId", event.orderId().toString());
            notifService.sendOrderConfirmed(event.orderId(), event.userId());
        } catch (Exception e) {
            log.error("Failed to process OrderCreatedEvent for notification", e);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_STATUS_CHANGED, groupId = "notification-service")
    public void onOrderStatusChanged(String message) {
        try {
            OrderStatusChangedEvent event = objectMapper.readValue(message, OrderStatusChangedEvent.class);
            MDC.put("orderId", event.orderId().toString());

            if ("COMPLETED".equals(event.toStatus())) {
                notifService.sendOrderCompleted(event.orderId(), event.userId());
            }
        } catch (Exception e) {
            log.error("Failed to process OrderStatusChangedEvent for notification", e);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_CANCELLED, groupId = "notification-service")
    public void onOrderCancelled(String message) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(message, OrderCancelledEvent.class);
            MDC.put("orderId", event.orderId().toString());
            notifService.sendOrderCancelled(event.orderId(), event.userId(), event.reason());
        } catch (Exception e) {
            log.error("Failed to process OrderCancelledEvent for notification", e);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service")
    public void onPaymentFailed(String message) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
            MDC.put("orderId", event.orderId().toString());
            notifService.sendPaymentFailed(event.orderId(), event.userId(), event.failureReason());
        } catch (Exception e) {
            log.error("Failed to process PaymentFailedEvent for notification", e);
        } finally {
            MDC.clear();
        }
    }
}
