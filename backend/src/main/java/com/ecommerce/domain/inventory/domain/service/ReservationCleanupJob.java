package com.ecommerce.domain.inventory.domain.service;

import com.ecommerce.common.status.CheckoutSessionStatus;
import com.ecommerce.common.status.InventoryReservationStatus;
import com.ecommerce.domain.inventory.domain.model.InventoryReservation;
import com.ecommerce.domain.inventory.domain.ports.in.ReservationCleanupUseCase;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryRepositoryPort;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryReservationRepositoryPort;
import com.ecommerce.domain.order.domain.model.CheckoutSession;
import com.ecommerce.domain.order.domain.ports.out.CheckoutSessionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ReservationCleanupJob implements ReservationCleanupUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservationCleanupJob.class);

    private final InventoryReservationRepositoryPort inventoryReservationRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final CheckoutSessionRepositoryPort checkoutSessionRepository;

    public ReservationCleanupJob(
            InventoryReservationRepositoryPort inventoryReservationRepository,
            InventoryRepositoryPort inventoryRepository,
            CheckoutSessionRepositoryPort checkoutSessionRepository) {
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
    }

    @Override
    public void cleanupExpiredReservations() {
        Instant now = Instant.now();
        List<InventoryReservation> expiredReservations =
                inventoryReservationRepository.findExpiredHoldingReservations(now);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("Found {} expired holding reservations to clean up.", expiredReservations.size());

        for (InventoryReservation res : expiredReservations) {
            try {
                // 1. Return Stock
                inventoryRepository.atomicIncrement(res.getProductId(), res.getQuantity());

                // 2. Update Reservation status to EXPIRED
                res.setStatus(InventoryReservationStatus.EXPIRED);
                inventoryReservationRepository.save(res);

                // 3. Update Session status to EXPIRED
                Optional<CheckoutSession> sessionOpt = Optional.empty();
                if (res.getOrderId() != null) {
                    sessionOpt = checkoutSessionRepository.findByOrderId(res.getOrderId());
                }
                if (sessionOpt.isEmpty()) {
                    List<CheckoutSession> sessions =
                            checkoutSessionRepository.findByExpiresAt(res.getExpiresAt());
                    if (!sessions.isEmpty()) {
                        sessionOpt = Optional.of(sessions.getFirst());
                    }
                }

                sessionOpt.ifPresent(
                        session -> {
                            if (session.getStatus() != CheckoutSessionStatus.SUCCESS
                                    && session.getStatus() != CheckoutSessionStatus.EXPIRED) {
                                session.setStatus(CheckoutSessionStatus.EXPIRED);
                                checkoutSessionRepository.save(session);
                                log.info(
                                        "Marked connected checkout session {} as EXPIRED.",
                                        session.getIdempotencyKey());
                            }
                        });

                log.info(
                        "Expired reservation cleaned up: reservation={}, product={}, quantity={}",
                        res.getId(),
                        res.getProductId(),
                        res.getQuantity());

            } catch (Exception e) {
                log.error("Failed to clean up expired reservation {}: {}", res.getId(), e.getMessage(), e);
            }
        }
    }
}
