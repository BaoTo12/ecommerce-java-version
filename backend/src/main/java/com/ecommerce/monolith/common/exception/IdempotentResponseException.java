package com.ecommerce.monolith.common.exception;

/**
 * Edge Case #1: Thrown when an idempotent cached response should be returned. The
 * GlobalExceptionHandler catches this and returns the cached payload.
 */
public class IdempotentResponseException extends RuntimeException {
  private final String key;
  private final int originalStatus;
  private final Object cachedBody;

  public IdempotentResponseException(String key, int originalStatus, Object cachedBody) {
    super("Duplicate request for idempotency key: " + key);
    this.key = key;
    this.originalStatus = originalStatus;
    this.cachedBody = cachedBody;
  }

  public String getKey() {
    return key;
  }

  public int getOriginalStatus() {
    return originalStatus;
  }

  public Object getCachedBody() {
    return cachedBody;
  }
}
