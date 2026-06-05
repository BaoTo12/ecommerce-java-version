package com.ecommerce.monolith.common.status;

import com.ecommerce.monolith.common.exception.IllegalOrderTransitionException;
import java.util.Map;
import java.util.Set;

/**
 * Edge Case #8 — Order State Machine
 *
 * <p>Problem: Without a state machine, code like order.setStatus("COMPLETED") can be called from
 * anywhere, allowing logically impossible transitions like: CANCELLED → COMPLETED, DELIVERED →
 * PENDING, etc.
 *
 * <p>Solution: canTransitionTo() defines the ONLY valid transitions. Any attempt to perform an
 * invalid transition throws IllegalOrderTransitionException which the GlobalExceptionHandler
 * converts to HTTP 409 Conflict.
 *
 * <p>Valid flow:
 *
 * <p>PENDING │ ▼ CONFIRMED ──────────────────────┐ │ │ ▼ ▼ PAYMENT_PENDING CANCELLED │ ┌──┴──┐ ▼ ▼
 * PAID PAYMENT_FAILED │ │ ▼ ▼ PROCESSING CANCELLED │ ▼ SHIPPED │ ▼ DELIVERED │ ▼ COMPLETED
 */
public enum OrderStatus {
  PENDING,
  CONFIRMED,
  PAYMENT_PENDING,
  PAID,
  PAYMENT_FAILED,
  PROCESSING,
  SHIPPED,
  DELIVERED,
  COMPLETED,
  CANCELLED,
  EXPIRED; // Edge Case #5: reservation TTL expired

  private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS =
      Map.ofEntries(
          Map.entry(PENDING, Set.of(CONFIRMED, CANCELLED, PAID, PAYMENT_PENDING, PAYMENT_FAILED)),
          Map.entry(CONFIRMED, Set.of(PAYMENT_PENDING, CANCELLED)),
          Map.entry(PAYMENT_PENDING, Set.of(PAID, PAYMENT_FAILED, CANCELLED, PENDING)),
          Map.entry(PAID, Set.of(PROCESSING, CANCELLED)),
          Map.entry(PAYMENT_FAILED, Set.of(CANCELLED, PENDING)),
          Map.entry(PROCESSING, Set.of(SHIPPED, CANCELLED)),
          Map.entry(SHIPPED, Set.of(DELIVERED)),
          Map.entry(DELIVERED, Set.of(COMPLETED)),
          Map.entry(COMPLETED, Set.of()),
          Map.entry(CANCELLED, Set.of()),
          Map.entry(EXPIRED, Set.of()));

  // Edge Case #8: Validates and performs the transition.
  // Throws IllegalOrderTransitionException if the transition is not allowed.
  public void validateTransitionTo(OrderStatus next) {
    Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(this, Set.of());
    if (!allowed.contains(next)) {
      throw new IllegalOrderTransitionException(this.name(), next.name());
    }
  }

  public boolean canTransitionTo(OrderStatus next) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
  }
}
