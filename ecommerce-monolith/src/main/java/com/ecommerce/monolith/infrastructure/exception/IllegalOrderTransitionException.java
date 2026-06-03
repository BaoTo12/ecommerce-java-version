package com.ecommerce.monolith.infrastructure.exception;

/** Edge Case #8: Order state machine — invalid transition attempted. */
public class IllegalOrderTransitionException extends RuntimeException {
  public IllegalOrderTransitionException(String from, String to) {
    super("Cannot transition order from " + from + " to " + to);
  }
}
