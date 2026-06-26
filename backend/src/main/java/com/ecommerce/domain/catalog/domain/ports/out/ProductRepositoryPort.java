package com.ecommerce.domain.catalog.domain.ports.out;

import com.ecommerce.domain.catalog.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {
    Page<Product> findByIsActiveTrue(Pageable pageable);
    long countByCategory(String category);
    Page<Product> searchActive(String keyword, String category, Pageable pageable);
    Optional<Product> findBySkuAndIsActiveTrue(String sku);
    Optional<Product> findById(UUID id);
    List<Product> findAllById(List<UUID> ids);
    Product save(Product product);
}
