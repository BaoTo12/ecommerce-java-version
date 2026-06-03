package com.ecommerce.monolith.infrastructure.exception;

/** Edge Case #16: Resource does not belong to the current user. */
public class ResourceOwnershipException extends RuntimeException {
  public ResourceOwnershipException(String resourceType, Object id) {
    super(resourceType + " " + id + " does not belong to the current user");
  }
}
