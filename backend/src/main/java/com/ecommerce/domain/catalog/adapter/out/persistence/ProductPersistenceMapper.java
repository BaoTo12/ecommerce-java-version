package com.ecommerce.domain.catalog.adapter.out.persistence;

import com.ecommerce.domain.catalog.domain.model.Product;

public class ProductPersistenceMapper {

    public static Product toDomain(ProductJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return Product.builder()
                .id(jpa.getId())
                .sku(jpa.getSku())
                .name(jpa.getName())
                .description(jpa.getDescription())
                .price(jpa.getPrice())
                .category(jpa.getCategory())
                .isActive(jpa.isActive())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .build();
    }

    public static ProductJpaEntity toJpa(Product domain) {
        if (domain == null) {
            return null;
        }
        return ProductJpaEntity.builder()
                .id(domain.getId())
                .sku(domain.getSku())
                .name(domain.getName())
                .description(domain.getDescription())
                .price(domain.getPrice())
                .category(domain.getCategory())
                .isActive(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
