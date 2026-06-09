package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
  public ResourceNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }

  public static ResourceNotFoundException of(String type, Object id) {
    return new ResourceNotFoundException(type + " not found: " + id);
  }
}
