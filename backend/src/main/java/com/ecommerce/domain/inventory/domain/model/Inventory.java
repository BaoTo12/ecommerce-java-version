package com.ecommerce.domain.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    private UUID productId;
    private int quantity;
    private long version;
    
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public boolean hasSufficientStock(int amount) {
        return this.quantity < amount;
    }

    public void reserve(int amount) {
        if (this.quantity < amount) {
            throw new IllegalStateException("Insufficient stock");
        }
        this.quantity -= amount;
        this.updatedAt = Instant.now();
    }
}
