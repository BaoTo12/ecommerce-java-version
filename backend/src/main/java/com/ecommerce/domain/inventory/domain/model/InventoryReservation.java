package com.ecommerce.domain.inventory.domain.model;

import com.ecommerce.common.status.InventoryReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservation {
    private UUID id;
    private UUID orderId;
    
    @Builder.Default
    private String operation = "RESERVE";
    
    private UUID productId;
    private int quantity;
    private InventoryReservationStatus status;
    private String failureReason;
    
    @Builder.Default
    private Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
    
    private Instant releasedAt;
    
    @Builder.Default
    private Instant createdAt = Instant.now();

    public void markReleased() {
        this.status = InventoryReservationStatus.FAILED;
        this.releasedAt = Instant.now();
    }
}
