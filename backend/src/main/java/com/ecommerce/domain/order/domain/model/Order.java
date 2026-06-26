package com.ecommerce.domain.order.domain.model;

import com.ecommerce.common.status.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private UUID id;
    private UUID userId;
    
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;
    
    private BigDecimal totalAmount;
    private UUID idempotencyKey;
    private UUID shippingAddressId;
    private String notes;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void transitionTo(OrderStatus next) {
        this.status.validateTransitionTo(next);
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
