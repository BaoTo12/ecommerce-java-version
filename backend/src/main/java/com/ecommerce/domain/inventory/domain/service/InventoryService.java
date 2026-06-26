package com.ecommerce.domain.inventory.domain.service;

import com.ecommerce.common.exception.BusinessRuleViolationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.status.InventoryReservationStatus;
import com.ecommerce.domain.inventory.domain.model.Inventory;
import com.ecommerce.domain.inventory.domain.model.InventoryReservation;
import com.ecommerce.domain.inventory.domain.ports.in.InventoryUseCase;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryRepositoryPort;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryReservationRepositoryPort;
import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.order.domain.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventoryService implements InventoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepositoryPort inventoryRepo;
    private final InventoryReservationRepositoryPort reservationRepo;
    private final int maxRetry;
    private final long backoffMs;
    private final long reservationTtlMinutes;
    private InventoryService self;

    public InventoryService(
            InventoryRepositoryPort inventoryRepo,
            InventoryReservationRepositoryPort reservationRepo,
            int maxRetry,
            long backoffMs,
            long reservationTtlMinutes) {
        this.inventoryRepo = inventoryRepo;
        this.reservationRepo = reservationRepo;
        this.maxRetry = maxRetry;
        this.backoffMs = backoffMs;
        this.reservationTtlMinutes = reservationTtlMinutes;
    }

    public void setSelf(InventoryService self) {
        this.self = self;
    }

    @Override
    public void reserveForOrder(Order order) {
        UUID orderId = order.getId();
        Instant expiresAt = Instant.now().plusSeconds(reservationTtlMinutes * 60);

        List<UUID> reservedProducts = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            UUID productId = item.getProductId();
            int quantity = item.getQuantity();

            int updated = inventoryRepo.atomicDecrement(productId, quantity);
            if (updated == 1) {
                reservationRepo.save(
                        InventoryReservation.builder()
                                .orderId(orderId)
                                .productId(productId)
                                .quantity(quantity)
                                .status(InventoryReservationStatus.SUCCEED)
                                .expiresAt(expiresAt)
                                .build());
                reservedProducts.add(productId);
                log.info("Atomic reserve: product={}, qty={}, order={}", productId, quantity, orderId);
                continue;
            }

            boolean success = reserveWithOptimisticRetry(orderId, productId, quantity, expiresAt);
            if (!success) {
                rollback(orderId, reservedProducts);
                throw new BusinessRuleViolationException("Insufficient stock for product: " + productId);
            }
            reservedProducts.add(productId);
        }
    }

    private boolean reserveWithOptimisticRetry(
            UUID orderId, UUID productId, int qty, Instant expiresAt) {
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                // Calls the proxy (self) to support REQUIRES_NEW transaction boundaries
                return self.doOptimisticReserve(orderId, productId, qty, expiresAt);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn(
                        "Optimistic lock conflict: product={}, attempt={}/{}", productId, attempt, maxRetry);
                if (attempt < maxRetry) {
                    sleep(backoffMs * attempt);
                }
            }
        }
        reservationRepo.save(
                InventoryReservation.builder()
                        .orderId(orderId)
                        .productId(productId)
                        .quantity(qty)
                        .status(InventoryReservationStatus.FAILED)
                        .failureReason("LOCK_EXHAUSTED")
                        .build());
        return false;
    }

    // This method will be intercepts by AOP transaction proxy and executes in REQUIRES_NEW
    public boolean doOptimisticReserve(UUID orderId, UUID productId, int qty, Instant expiresAt) {
        Inventory inv = inventoryRepo
                .findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inventory", productId));

        if (inv.hasSufficientStock(qty)) {
            reservationRepo.save(
                    InventoryReservation.builder()
                            .orderId(orderId)
                            .productId(productId)
                            .quantity(qty)
                            .status(InventoryReservationStatus.FAILED)
                            .failureReason("INSUFFICIENT_STOCK")
                            .build());
            return false;
        }

        inv.reserve(qty);
        inventoryRepo.save(inv);
        reservationRepo.save(
                InventoryReservation.builder()
                        .orderId(orderId)
                        .productId(productId)
                        .quantity(qty)
                        .status(InventoryReservationStatus.SUCCEED)
                        .expiresAt(expiresAt)
                        .build());
        return true;
    }

    @Override
    public void releaseExpiredReservation(InventoryReservation res) {
        inventoryRepo.atomicIncrement(res.getProductId(), res.getQuantity());
        res.markReleased();
        reservationRepo.save(res);
        log.info("TTL expiry released: product={}, qty={}", res.getProductId(), res.getQuantity());
    }

    @Override
    public int getStock(UUID productId) {
        return inventoryRepo
                .findById(productId)
                .map(Inventory::getQuantity)
                .orElseThrow(() -> ResourceNotFoundException.of("Inventory", productId));
    }

    private void rollback(UUID orderId, List<UUID> reservedProductIds) {
        for (UUID productId : reservedProductIds) {
            try {
                inventoryRepo.atomicIncrement(productId, 1);
                log.info("Rollback release: product={}, order={}", productId, orderId);
            } catch (Exception e) {
                log.error("Rollback failed: product={}", productId, e);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
