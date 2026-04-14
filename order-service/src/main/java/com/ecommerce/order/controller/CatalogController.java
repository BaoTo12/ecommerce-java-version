package com.ecommerce.order.controller;

import com.ecommerce.order.model.dto.ProductResponse;
import com.ecommerce.order.service.CatalogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/catalog/products")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public Page<ProductResponse> listProducts(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return catalogService.listProducts(keyword, pageable);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(catalogService.getProduct(productId));
    }
}
