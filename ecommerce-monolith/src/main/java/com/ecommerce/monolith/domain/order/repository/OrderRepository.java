package com.ecommerce.monolith.domain.order.repository;

import com.ecommerce.monolith.domain.order.entity.OrderEntity;
import com.ecommerce.monolith.domain.order.enums.OrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Edge Case #23 — N+1 Query Prevention:
 *
 * <p>Problem: The naïve approach is to fetch a list of orders, then for each order Hibernate
 * lazy-loads the items collection separately:
 *
 * <p>SELECT * FROM orders WHERE user_id = ? ← 1 query SELECT * FROM order_items WHERE order_id = ?
 * ← for EACH order (N queries)
 *
 * <p>With 50 orders per page → 51 DB queries per request. At 1000 concurrent users → 51,000
 * queries/sec. Database melts.
 *
 * <p>Solution: JOIN FETCH loads orders AND their items in a single query.
 *
 * <p>SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.userId = :userId
 *
 * <p>This becomes: SELECT o.*, i.* FROM orders o LEFT JOIN order_items i ON i.order_id = o.id WHERE
 * o.user_id = ? ← 1 query, all data fetched
 *
 * <p>Trade-off: Pagination with JOIN FETCH is tricky. When using JOIN FETCH with pagination,
 * Hibernate fetches ALL rows in memory then paginates — this is the "HHH90003004" warning. Fix: use
 * two queries: Query 1: get page of order IDs (paginated, no join) Query 2: fetch full orders for
 * those IDs (JOIN FETCH, no pagination) This pattern is implemented in findPageByUserId below.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

  /**
   * Edge Case #23: Pagination-safe N+1 fix. Step 1: Get paginated order IDs only (fast, no join).
   */
  @Query("SELECT o.id FROM OrderEntity o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
  Page<UUID> findOrderIdsByUserId(UUID userId, Pageable pageable);

  /**
   * Edge Case #23: Step 2: Fetch full orders with items for those IDs. JOIN FETCH without
   * pagination — no memory explosion risk.
   */
  @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
  java.util.List<OrderEntity> findByIdsWithItems(java.util.List<UUID> ids);

  /** Single order with items — safe to JOIN FETCH (only one row). */
  @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
  Optional<OrderEntity> findByIdWithItems(UUID id);

  Page<OrderEntity> findByUserId(UUID userId, Pageable pageable);

  Page<OrderEntity> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

  /** Edge Case #1 — Idempotency: find an existing order by idempotency key. */
  Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
