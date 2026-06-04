package com.ecommerce.monolith.domain.catalog.service;

import com.ecommerce.monolith.domain.catalog.dto.ProductResponse;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Catalog Service — product browse / search. Edge Case #20: keyword trimmed and length-validated
 * before use.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

  private final ProductRepository productRepo;

  public CatalogService(ProductRepository productRepo) {
    this.productRepo = productRepo;
  }

  public Page<ProductResponse> listProducts(String keyword, Pageable pageable) {
    if (StringUtils.hasText(keyword)) {
      // Edge Case #20: trim input, max 100 chars to prevent excessive queries
      String sanitized = keyword.trim().substring(0, Math.min(keyword.trim().length(), 100));
      return productRepo.searchActive(sanitized, pageable).map(this::toResponse);
    }
    return productRepo.findByIsActiveTrue(pageable).map(this::toResponse);
  }

  public ProductResponse getProduct(UUID productId) {
    return productRepo
        .findById(productId)
        .filter(Product::isActive)
        .map(this::toResponse)
        .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
  }

  private ProductResponse toResponse(Product p) {
    return new ProductResponse(
        p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(), p.getCreatedAt());
  }
}
