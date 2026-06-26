package com.ecommerce.domain.inventory.domain.ports.in;

public interface ReservationCleanupUseCase {
    void cleanupExpiredReservations();
}
