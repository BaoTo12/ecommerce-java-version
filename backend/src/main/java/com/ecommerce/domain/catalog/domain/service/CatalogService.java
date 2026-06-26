package com.ecommerce.domain.catalog.domain.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.domain.catalog.domain.model.Product;
import com.ecommerce.domain.catalog.domain.ports.in.CatalogUseCase;
import com.ecommerce.domain.catalog.domain.ports.out.ProductRepositoryPort;
import com.ecommerce.domain.catalog.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class CatalogService implements CatalogUseCase {

    private final ProductRepositoryPort productRepo;

    public CatalogService(ProductRepositoryPort productRepo) {
        this.productRepo = productRepo;
    }

    @Override
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        return productRepo.findByIsActiveTrue(pageable).map(this::toResponse);
    }

    @Override
    public ProductResponse getProductById(UUID id) {
        return productRepo.findById(id)
                .filter(Product::isActive)
                .map(this::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    @Override
    public Page<ProductResponse> searchProducts(String keyword, String category, Pageable pageable) {
        String sanitizedKeyword = null;
        if (StringUtils.hasText(keyword)) {
            sanitizedKeyword = keyword.trim().substring(0, Math.min(keyword.trim().length(), 100));
        }
        String sanitizedCategory = StringUtils.hasText(category) ? category.trim() : null;

        return productRepo.searchActive(sanitizedKeyword, sanitizedCategory, pageable).map(this::toResponse);
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
