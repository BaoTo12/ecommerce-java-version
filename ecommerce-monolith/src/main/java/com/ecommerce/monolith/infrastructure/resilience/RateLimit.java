package com.ecommerce.monolith.infrastructure.resilience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Edge Case #12 — Rate Limiting per User
 *
 * <p>Annotate any controller method to apply per-user rate limiting.
 *
 * <p>Example: {@code @RateLimit(maxRequests = 10, windowSeconds = 60)} public
 * ResponseEntity<OrderResponse> createOrder(...) {...}
 *
 * <p>Returns HTTP 429 Too Many Requests if the user exceeds the limit. The limit resets after the
 * window expires (sliding window).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
  /** Maximum number of requests allowed within the time window. */
  int maxRequests() default 20;

  /** Time window in seconds. */
  int windowSeconds() default 60;

  /** Human-readable name for logging/error messages. */
  String name() default "default";
}
