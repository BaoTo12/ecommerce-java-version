package com.ecommerce.domain.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private UUID id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
