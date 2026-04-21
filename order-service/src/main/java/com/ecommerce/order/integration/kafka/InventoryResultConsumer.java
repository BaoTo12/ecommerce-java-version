package com.ecommerce.order.integration.kafka;

import com.ecommerce.order.integration.IdempotencyStore;
import com.ecommerce.order.service.OrderSagaService;
import com.ecommerce.shared.event.InventoryFailedEvent;
import com.ecommerce.shared.event.InventoryReservedEvent;
import com.ecommerce.shared.event.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryResultConsumer.class);
    private final OrderSagaService sagaService;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public InventoryResultConsumer(OrderSagaService sagaService, IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper) {
        this.sagaService = sagaService;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "order-service")
    public void onInventoryReserved(String message) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(message, InventoryReservedEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            if (!idempotencyStore.tryAcquire("order", "inventory-reserved", orderId))
                return;

            sagaService.handleInventoryReserved(event);
        } catch (Exception e) {
            log.error("Failed to process InventoryReservedEvent", e);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_FAILED, groupId = "order-service")
    public void onInventoryFailed(String message) {
        try {
            InventoryFailedEvent event = objectMapper.readValue(message, InventoryFailedEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            if (!idempotencyStore.tryAcquire("order", "inventory-failed", orderId))
                return;

            sagaService.handleInventoryFailed(event);
        } catch (Exception e) {
            log.error("Failed to process InventoryFailedEvent", e);
        } finally {
            MDC.clear();
        }
    }
}
