package com.ecommerce.order.integration.kafka;

import com.ecommerce.order.integration.IdempotencyStore;
import com.ecommerce.order.service.OrderSagaService;
import com.ecommerce.shared.event.KafkaTopics;
import com.ecommerce.shared.event.PaymentFailedEvent;
import com.ecommerce.shared.event.PaymentSucceededEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);
    private final OrderSagaService sagaService;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public PaymentResultConsumer(OrderSagaService sagaService, IdempotencyStore idempotencyStore,
                                 ObjectMapper objectMapper) {
        this.sagaService = sagaService;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCEEDED, groupId = "order-service")
    public void onPaymentSucceeded(String message) {
        try {
            PaymentSucceededEvent event = objectMapper.readValue(message, PaymentSucceededEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            if (!idempotencyStore.tryAcquire("order", "payment-succeeded", orderId)) return;

            sagaService.handlePaymentSucceeded(event);
        } catch (Exception e) {
            log.error("Failed to process PaymentSucceededEvent", e);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-service")
    public void onPaymentFailed(String message) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            if (!idempotencyStore.tryAcquire("order", "payment-failed", orderId)) return;

            sagaService.handlePaymentFailed(event);
        } catch (Exception e) {
            log.error("Failed to process PaymentFailedEvent", e);
        } finally {
            MDC.clear();
        }
    }
}
