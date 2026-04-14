package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.ProductCatalogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface ProductCatalogRepository extends JpaRepository<ProductCatalogEntity, UUID> {
    @Query("SELECT p FROM ProductCatalogEntity p WHERE p.isActive = true")
    Page<ProductCatalogEntity> findAllActive(Pageable pageable);

    @Query("SELECT p FROM ProductCatalogEntity p WHERE p.isActive = true AND LOWER(p.name) LIKE LOWER(CONCAT('%',:keyword,'%'))")
    Page<ProductCatalogEntity> searchByName(String keyword, Pageable pageable);
}
