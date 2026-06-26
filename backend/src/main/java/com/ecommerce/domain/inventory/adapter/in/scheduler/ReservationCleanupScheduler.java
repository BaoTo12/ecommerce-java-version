package com.ecommerce.domain.inventory.adapter.in.scheduler;

import com.ecommerce.domain.inventory.domain.ports.in.ReservationCleanupUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationCleanupScheduler {

    private final ReservationCleanupUseCase cleanupUseCase;

    public ReservationCleanupScheduler(ReservationCleanupUseCase cleanupUseCase) {
        this.cleanupUseCase = cleanupUseCase;
    }

    @Scheduled(fixedDelay = 60000) // Runs every 1 minute
    public void cleanupExpiredReservations() {
        cleanupUseCase.cleanupExpiredReservations();
    }
}
