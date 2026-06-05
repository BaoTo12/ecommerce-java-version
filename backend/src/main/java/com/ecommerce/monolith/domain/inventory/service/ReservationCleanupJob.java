package com.ecommerce.monolith.domain.inventory.service;

import com.ecommerce.monolith.common.status.CheckoutSessionStatus;
import com.ecommerce.monolith.common.status.InventoryReservationStatus;
import com.ecommerce.monolith.domain.inventory.entity.InventoryReservation;
import com.ecommerce.monolith.domain.inventory.repository.InventoryRepository;
import com.ecommerce.monolith.domain.inventory.repository.InventoryReservationRepository;
import com.ecommerce.monolith.domain.order.entity.CheckoutSession;
import com.ecommerce.monolith.domain.order.repository.CheckoutSessionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationCleanupJob {

    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryRepository inventoryRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;

    @Scheduled(fixedDelay = 60000) // Runs every 1 minute
    @Transactional
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
