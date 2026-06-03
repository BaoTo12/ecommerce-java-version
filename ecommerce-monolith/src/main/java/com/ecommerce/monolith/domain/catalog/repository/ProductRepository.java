package com.ecommerce.monolith.domain.catalog.repository;

import com.ecommerce.monolith.domain.catalog.entity.ProductEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

  Page<ProductEntity> findByIsActiveTrue(Pageable pageable);

  /**
   * Edge Case #20 — Input Sanitization: Uses named parameter (:keyword) — Spring Data JPA generates
   * a parameterized query, preventing SQL injection.
   */
  @Query(
      "SELECT p FROM ProductEntity p WHERE p.isActive = true "
          + "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
          + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<ProductEntity> searchActive(String keyword, Pageable pageable);

  Optional<ProductEntity> findBySkuAndIsActiveTrue(String sku);
}
