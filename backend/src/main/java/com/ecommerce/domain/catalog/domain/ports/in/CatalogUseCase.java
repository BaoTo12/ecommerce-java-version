package com.ecommerce.domain.catalog.domain.ports.in;

import com.ecommerce.domain.catalog.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface CatalogUseCase {
    Page<ProductResponse> getActiveProducts(Pageable pageable);
    ProductResponse getProductById(UUID id);
    Page<ProductResponse> searchProducts(String keyword, String category, Pageable pageable);
}
