package com.ecommerce.monolith.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends AppException {
  public BusinessRuleViolationException(String message) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
