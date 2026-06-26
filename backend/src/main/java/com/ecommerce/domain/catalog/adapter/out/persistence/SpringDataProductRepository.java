package com.ecommerce.domain.catalog.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataProductRepository extends JpaRepository<ProductJpaEntity, UUID> {

  Page<ProductJpaEntity> findByIsActiveTrue(Pageable pageable);

  long countByCategory(String category);

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
  Page<ProductJpaEntity> searchActive(
      @Param("keyword") String keyword,
      @Param("category") String category,
      Pageable pageable);

  Optional<ProductJpaEntity> findBySkuAndIsActiveTrue(String sku);
}
