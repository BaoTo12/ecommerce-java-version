package com.ecommerce.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * E-commerce Monolith Application
 *
 * <p>A production-grade monolith that consolidates: - user-service → domain.user - order-service →
 * domain.order - inventory-service → domain.inventory - payment-service → domain.payment -
 * notification-service → domain.notification - gateway-service →
 * infrastructure.config.SecurityConfig (JWT inline)
 *
 * <p>Implements 20 senior-level edge cases: 1. Idempotency (X-Idempotency-Key) 2. Optimistic
 * locking with retry (inventory oversell prevention) 3. Pessimistic locking (double-payment
 * prevention) 4. Price snapshot at cart add-time 5. Inventory reservation TTL / expiry 6. Soft
 * delete (users) 7. Concurrent cart modification lock 8. Order state machine validation 9.
 * Duplicate payment prevention (DB UNIQUE) 10. Refund state machine + idempotency 11. Outbox
 * pattern (event reliability) 12. Rate limiting per user (AOP annotation) 13. Read-after-write
 * consistency 14. Atomic SQL decrement for flash sales 15. JWT token version invalidation 16.
 * Address ownership authorization 17. Checkout atomicity (cart→order single transaction) 18.
 * Notification deduplication 19. Graceful shutdown 20. Input sanitization
 */
@SpringBootApplication
@EnableScheduling
public class EcommerceMonolithApplication {
  public static void main(String[] args) {
    SpringApplication.run(EcommerceMonolithApplication.class, args);
  }
}
