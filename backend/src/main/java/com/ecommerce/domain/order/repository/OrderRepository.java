package com.ecommerce.domain.order.repository;

import com.ecommerce.domain.order.entity.Order;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Edge Case #23: Pagination-safe N+1 fix. Step 1: Get paginated order IDs only (fast, no join).
    @Query("SELECT o.id FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    Page<UUID> findOrderIdsByUserId(UUID userId, Pageable pageable);

    // Edge Case #23: Step 2: Fetch full orders with items for those IDs. JOIN FETCH without
    // pagination — no memory explosion risk.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    java.util.List<Order> findByIdsWithItems(java.util.List<UUID> ids);

    // Single order with items — safe to JOIN FETCH (only one row).
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(UUID id);

    Page<Order> findByUserId(UUID userId, Pageable pageable);


}
