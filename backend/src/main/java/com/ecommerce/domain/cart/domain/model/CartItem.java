package com.ecommerce.domain.cart.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private UUID id;
    private UUID productId;
    private String productName;
    private int quantity;
    private BigDecimal priceSnapshot;
    
    @Builder.Default
    private Instant addedAt = Instant.now();
    
    @Builder.Default
    private boolean selected = true;

    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }
}
