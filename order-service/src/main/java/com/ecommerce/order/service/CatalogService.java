package com.ecommerce.order.service;

import com.ecommerce.order.model.dto.ProductResponse;
import com.ecommerce.order.model.entity.ProductCatalogEntity;
import com.ecommerce.order.repository.ProductCatalogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductCatalogRepository productRepo;

    public CatalogService(ProductCatalogRepository productRepo) {
        this.productRepo = productRepo;
    }

    public Page<ProductResponse> listProducts(String keyword, Pageable pageable) {
        Page<ProductCatalogEntity> page;
        if (keyword != null && !keyword.isBlank()) {
            page = productRepo.searchByName(keyword, pageable);
        } else {
            page = productRepo.findAllActive(pageable);
        }
        return page.map(this::toResponse);
    }

    public ProductResponse getProduct(UUID productId) {
        return productRepo.findById(productId)
                .filter(ProductCatalogEntity::isActive)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
    }

    private ProductResponse toResponse(ProductCatalogEntity e) {
        return new ProductResponse(e.getId(), e.getSku(), e.getName(), e.getDescription(), e.getPrice());
    }
}
