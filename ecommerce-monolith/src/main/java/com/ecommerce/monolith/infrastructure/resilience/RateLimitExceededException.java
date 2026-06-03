package com.ecommerce.monolith.infrastructure.resilience;

public class RateLimitExceededException extends RuntimeException {
  private final int retryAfterSeconds;

  public RateLimitExceededException(String endpoint, int windowSeconds) {
    super("Rate limit exceeded for '" + endpoint + "'. Retry after " + windowSeconds + " seconds.");
    this.retryAfterSeconds = windowSeconds;
  }

  public int getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
