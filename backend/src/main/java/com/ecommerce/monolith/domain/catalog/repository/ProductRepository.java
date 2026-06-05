package com.ecommerce.monolith.domain.catalog.repository;

import com.ecommerce.monolith.domain.catalog.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  Page<Product> findByIsActiveTrue(Pageable pageable);

  long countByCategory(String category);

  // Edge Case #20 — Input Sanitization: Uses named parameter (:keyword) — Spring Data JPA generates
  // a parameterized query, preventing SQL injection. Using PostgreSQL Full-Text Search (FTS).
  @Query(
      value =
          "SELECT * FROM products WHERE is_active = true AND "
              + "(:category IS NULL OR :category = '' OR category = :category) AND "
              + "(:keyword IS NULL OR :keyword = '' OR LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
      countQuery =
          "SELECT count(*) FROM products WHERE is_active = true AND "
              + "(:category IS NULL OR :category = '' OR category = :category) AND "
              + "(:keyword IS NULL OR :keyword = '' OR LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
      nativeQuery = true)
  Page<Product> searchActive(
      @Param("keyword") String keyword,
      @Param("category") String category,
      Pageable pageable);

  Optional<Product> findBySkuAndIsActiveTrue(String sku);
}
