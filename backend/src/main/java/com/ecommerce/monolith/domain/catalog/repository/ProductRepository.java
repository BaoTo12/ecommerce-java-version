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

  // Edge Case #20 — Input Sanitization: Uses named parameter (:keyword) — Spring Data JPA generates
  // a parameterized query, preventing SQL injection. Using PostgreSQL Full-Text Search (FTS).
  @Query(
      value =
          "SELECT * FROM products WHERE is_active = true AND "
              + "(:keyword IS NULL OR :keyword = '' OR search_vector @@ websearch_to_tsquery('english', :keyword))",
      countQuery =
          "SELECT count(*) FROM products WHERE is_active = true AND "
              + "(:keyword IS NULL OR :keyword = '' OR search_vector @@ websearch_to_tsquery('english', :keyword))",
      nativeQuery = true)
  Page<Product> searchActive(@Param("keyword") String keyword, Pageable pageable);

  Optional<Product> findBySkuAndIsActiveTrue(String sku);
}
