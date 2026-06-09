package com.ecommerce.domain.catalog.controller;

import com.ecommerce.domain.catalog.dto.ProductResponse;
import com.ecommerce.domain.catalog.service.CatalogService;
import jakarta.validation.constraints.Size;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/catalog")
@Validated
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public Page<ProductResponse> listProducts(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 100) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return catalogService.listProducts(keyword, category, pageable);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(catalogService.getProduct(productId));
    }
}
