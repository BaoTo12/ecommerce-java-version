package com.ecommerce.order.model.enums;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PAYMENT_PROCESSING,
    PAID,
    PAYMENT_FAILED,
    FULFILLING,
    COMPLETED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            PENDING,            Set.of(CONFIRMED, CANCELLED),
            CONFIRMED,          Set.of(PAYMENT_PROCESSING, CANCELLED),
            PAYMENT_PROCESSING, Set.of(PAID, PAYMENT_FAILED),
            PAID,               Set.of(FULFILLING),
            PAYMENT_FAILED,     Set.of(CANCELLED),
            FULFILLING,         Set.of(COMPLETED),
            COMPLETED,          Set.of(),
            CANCELLED,          Set.of()
    );

    public void validateTransition(OrderStatus next) {
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(this, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateException(
                    "Invalid transition: %s → %s. Allowed: %s".formatted(this, next, allowed));
        }
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
