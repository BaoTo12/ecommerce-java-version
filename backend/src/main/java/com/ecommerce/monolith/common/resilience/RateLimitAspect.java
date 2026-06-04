package com.ecommerce.monolith.common.resilience;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Edge Case #12 — Rate Limiting per User (AOP Aspect)
 *
 * <p>Uses a fixed-window counter per (userId, endpoint) pair. Data is held in-memory with a
 * ConcurrentHashMap.
 *
 * <p>In production, replace this with Redis INCR + EXPIRE for: - Multi-instance support (shared
 * counter) - Automatic expiry without a cleanup thread
 *
 * <p>Algorithm: - Key = "userId:endpointName" - Value = WindowEntry(count, windowStartEpoch) - If
 * current time > windowStart + windowSeconds → reset counter - If count >= maxRequests → throw
 * RateLimitExceededException
 */
@Aspect
@Component
public class RateLimitAspect {

  private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

  private final ConcurrentHashMap<String, WindowEntry> windowMap = new ConcurrentHashMap<>();

  @Around("@annotation(rateLimit)")
  public Object enforce(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
    String userId = resolveUserId();
    if (userId == null) {
      // Anonymous user — skip rate limiting (security layer handles auth)
      return pjp.proceed();
    }

    String key = userId + ":" + rateLimit.name();
    long now = Instant.now().getEpochSecond();

    WindowEntry entry =
        windowMap.compute(
            key,
            (k, existing) -> {
              if (existing == null || (now - existing.windowStart) >= rateLimit.windowSeconds()) {
                // Window expired or first request — reset
                return new WindowEntry(1, now);
              }
              existing.count.incrementAndGet();
              return existing;
            });

    if (entry.count.get() > rateLimit.maxRequests()) {
      long retryAfter = rateLimit.windowSeconds() - (now - entry.windowStart);
      log.warn(
          "Rate limit exceeded: userId={}, endpoint={}, count={}",
          userId,
          rateLimit.name(),
          entry.count.get());
      throw new RateLimitExceededException(rateLimit.name(), (int) Math.max(1, retryAfter));
    }

    log.debug(
        "Rate limit check passed: userId={}, endpoint={}, count={}/{}",
        userId,
        rateLimit.name(),
        entry.count.get(),
        rateLimit.maxRequests());

    return pjp.proceed();
  }

  private String resolveUserId() {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null || !auth.isAuthenticated()) return null;
      return auth.getPrincipal().toString();
    } catch (Exception e) {
      return null;
    }
  }

  /** Fixed-window counter entry. */
  private static class WindowEntry {
    final AtomicInteger count;
    final long windowStart;

    WindowEntry(int initialCount, long windowStart) {
      this.count = new AtomicInteger(initialCount);
      this.windowStart = windowStart;
    }
  }
}
