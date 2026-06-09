package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/** Edge Case #8: Order state machine — invalid transition attempted. */
public class IllegalOrderTransitionException extends AppException {
  public IllegalOrderTransitionException(String from, String to) {
    super("Cannot transition order from " + from + " to " + to, HttpStatus.CONFLICT);
  }
}
