package com.ecommerce.inventory.service;

import com.ecommerce.inventory.model.dto.InventoryResponse;
import com.ecommerce.inventory.model.entity.InventoryEntity;
import com.ecommerce.inventory.model.entity.InventoryReservationEntity;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.ecommerce.shared.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepo;
    private final InventoryReservationRepository reservationRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int maxRetry;
    private final long backoffMs;

    public InventoryService(InventoryRepository inventoryRepo,
                            InventoryReservationRepository reservationRepo,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            @Value("${app.retry.max-attempts:3}") int maxRetry,
                            @Value("${app.retry.backoff-ms:50}") long backoffMs) {
        this.inventoryRepo = inventoryRepo;
        this.reservationRepo = reservationRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.maxRetry = maxRetry;
        this.backoffMs = backoffMs;
    }

    /**
     * Reserve stock for all items in an order. Uses optimistic locking with retry.
     */
    public void reserveForOrder(OrderCreatedEvent event) {
        UUID orderId = event.orderId();
        List<InventoryReservedEvent.ReservedItem> reserved = new ArrayList<>();

        for (OrderCreatedEvent.OrderItemPayload item : event.items()) {
            boolean success = reserveWithRetry(orderId, item.productId(), item.quantity());
            if (!success) {
                // Rollback already-reserved items
                rollbackReservations(orderId, reserved);

                // Publish failure
                publishEvent(KafkaTopics.INVENTORY_FAILED,
                        orderId.toString(),
                        new InventoryFailedEvent(orderId, item.productId(), item.quantity(),
                                getAvailableQty(item.productId()), "INSUFFICIENT_STOCK", Instant.now()));
                return;
            }
            reserved.add(new InventoryReservedEvent.ReservedItem(item.productId(), item.quantity()));
        }

        // All items reserved successfully
        publishEvent(KafkaTopics.INVENTORY_RESERVED,
                orderId.toString(),
                new InventoryReservedEvent(orderId, reserved, Instant.now()));
        log.info("Inventory reserved for order: {}", orderId);
    }

    private boolean reserveWithRetry(UUID orderId, UUID productId, int quantity) {
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                return doReserve(orderId, productId, quantity);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on product={}, attempt={}/{}", productId, attempt, maxRetry);
                if (attempt < maxRetry) {
                    sleep(backoffMs * attempt);
                }
            }
        }
        // All retries exhausted
        reservationRepo.save(InventoryReservationEntity.failed(orderId, productId, quantity, "RESERVE", "CONCURRENT_CONFLICT"));
        return false;
    }

    @Transactional
    public boolean doReserve(UUID orderId, UUID productId, int quantity) {
        InventoryEntity inv = inventoryRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found in inventory: " + productId));

        if (!inv.hasSufficientStock(quantity)) {
            reservationRepo.save(InventoryReservationEntity.failed(orderId, productId, quantity, "RESERVE", "INSUFFICIENT_STOCK"));
            return false;
        }

        inv.reserve(quantity);
        inventoryRepo.save(inv); // @Version check happens here
        reservationRepo.save(InventoryReservationEntity.success(orderId, productId, quantity, "RESERVE"));
        log.info("Reserved {} units of product={} for order={}", quantity, productId, orderId);
        return true;
    }

    /**
     * Release previously reserved stock (compensating transaction for saga rollback).
     */
    @Transactional
    public void releaseForOrder(InventoryReleaseRequestedEvent event) {
        UUID orderId = event.orderId();

        for (OrderCreatedEvent.OrderItemPayload item : event.items()) {
            try {
                InventoryEntity inv = inventoryRepo.findById(item.productId())
                        .orElseThrow(() -> new RuntimeException("Product not found: " + item.productId()));
                inv.release(item.quantity());
                inventoryRepo.save(inv);
                reservationRepo.save(InventoryReservationEntity.success(orderId, item.productId(), item.quantity(), "RELEASE"));
                log.info("Released {} units of product={} for order={}", item.quantity(), item.productId(), orderId);
            } catch (Exception e) {
                log.error("Failed to release inventory for product={}, order={}", item.productId(), orderId, e);
                reservationRepo.save(InventoryReservationEntity.failed(orderId, item.productId(), item.quantity(), "RELEASE", e.getMessage()));
            }
        }

        publishEvent(KafkaTopics.INVENTORY_RELEASED,
                orderId.toString(),
                new InventoryReleasedEvent(orderId, Instant.now()));
    }

    @Transactional(readOnly = true)
    public InventoryResponse getStock(UUID productId) {
        InventoryEntity inv = inventoryRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found in inventory: " + productId));
        return new InventoryResponse(inv.getProductId(), inv.getQuantity(), inv.getVersion());
    }

    private void rollbackReservations(UUID orderId, List<InventoryReservedEvent.ReservedItem> reserved) {
        for (var item : reserved) {
            try {
                InventoryEntity inv = inventoryRepo.findById(item.productId()).orElseThrow();
                inv.release(item.quantity());
                inventoryRepo.save(inv);
                reservationRepo.save(InventoryReservationEntity.success(orderId, item.productId(), item.quantity(), "RELEASE"));
            } catch (Exception e) {
                log.error("Failed to rollback reservation for product={}", item.productId(), e);
            }
        }
    }

    private int getAvailableQty(UUID productId) {
        return inventoryRepo.findById(productId).map(InventoryEntity::getQuantity).orElse(0);
    }

    private void publishEvent(String topic, String key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.error("Failed to publish event to topic={}", topic, e);
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
