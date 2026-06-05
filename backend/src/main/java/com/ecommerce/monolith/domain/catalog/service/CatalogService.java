package com.ecommerce.monolith.domain.catalog.service;

import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.domain.catalog.dto.ProductResponse;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CatalogService {

  private final ProductRepository productRepo;

  public CatalogService(ProductRepository productRepo) {
    this.productRepo = productRepo;
  }

  public Page<ProductResponse> listProducts(String keyword, String category, Pageable pageable) {
    String sanitizedKeyword = null;
    if (StringUtils.hasText(keyword)) {
      sanitizedKeyword = keyword.trim().substring(0, Math.min(keyword.trim().length(), 100));
    }
    String sanitizedCategory = StringUtils.hasText(category) ? category.trim() : null;

    // Use the unified searchActive query which handles optional keyword and category filters natively
    return productRepo.searchActive(sanitizedKeyword, sanitizedCategory, pageable).map(this::toResponse);
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
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getDescription(),
        p.getPrice(),
        p.getCategory(),
        p.getCreatedAt());
  }
}
