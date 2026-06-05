package com.ecommerce.monolith.common.exception;

import org.springframework.http.HttpStatus;

/** Edge Case #16: Resource does not belong to the current user. */
public class ResourceOwnershipException extends AppException {
  public ResourceOwnershipException(String resourceType, Object id) {
    super(resourceType + " " + id + " does not belong to the current user", HttpStatus.FORBIDDEN);
  }
}
