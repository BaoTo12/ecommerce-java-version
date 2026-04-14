package com.ecommerce.inventory.integration.kafka;

import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.shared.event.InventoryReleaseRequestedEvent;
import com.ecommerce.shared.event.KafkaTopics;
import com.ecommerce.shared.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Collections;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final Set<String> processedEvents = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public InventoryEventConsumer(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_CREATED, groupId = "inventory-service")
    public void onOrderCreated(String message) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            // Idempotency check
            String key = "inventory:order-created:" + orderId;
            if (!processedEvents.add(key)) {
                log.warn("Duplicate OrderCreatedEvent skipped: {}", orderId);
                return;
            }

            inventoryService.reserveForOrder(event);
        } catch (Exception e) {
            log.error("Failed to process OrderCreatedEvent", e);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_RELEASE_REQUESTED, groupId = "inventory-service")
    public void onReleaseRequested(String message) {
        try {
            InventoryReleaseRequestedEvent event = objectMapper.readValue(message, InventoryReleaseRequestedEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            String key = "inventory:release-requested:" + orderId;
            if (!processedEvents.add(key)) {
                log.warn("Duplicate InventoryReleaseRequestedEvent skipped: {}", orderId);
                return;
            }

            inventoryService.releaseForOrder(event);
        } catch (Exception e) {
            log.error("Failed to process InventoryReleaseRequestedEvent", e);
        } finally {
            MDC.clear();
        }
    }
}
