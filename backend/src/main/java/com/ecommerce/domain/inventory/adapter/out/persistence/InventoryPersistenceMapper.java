package com.ecommerce.domain.inventory.adapter.out.persistence;

import com.ecommerce.domain.inventory.domain.model.Inventory;
import com.ecommerce.domain.inventory.domain.model.InventoryReservation;

public class InventoryPersistenceMapper {

    public static Inventory toDomain(InventoryJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return Inventory.builder()
                .productId(jpa.getProductId())
                .quantity(jpa.getQuantity())
                .version(jpa.getVersion())
                .updatedAt(jpa.getUpdatedAt())
                .build();
    }

    public static InventoryJpaEntity toJpa(Inventory domain) {
        if (domain == null) {
            return null;
        }
        return InventoryJpaEntity.builder()
                .productId(domain.getProductId())
                .quantity(domain.getQuantity())
                .version(domain.getVersion())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public static InventoryReservation toDomain(InventoryReservationJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return InventoryReservation.builder()
                .id(jpa.getId())
                .orderId(jpa.getOrderId())
                .operation(jpa.getOperation())
                .productId(jpa.getProductId())
                .quantity(jpa.getQuantity())
                .status(jpa.getStatus())
                .failureReason(jpa.getFailureReason())
                .expiresAt(jpa.getExpiresAt())
                .releasedAt(jpa.getReleasedAt())
                .createdAt(jpa.getCreatedAt())
                .build();
    }

    public static InventoryReservationJpaEntity toJpa(InventoryReservation domain) {
        if (domain == null) {
            return null;
        }
        return InventoryReservationJpaEntity.builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .operation(domain.getOperation())
                .productId(domain.getProductId())
                .quantity(domain.getQuantity())
                .status(domain.getStatus())
                .failureReason(domain.getFailureReason())
                .expiresAt(domain.getExpiresAt())
                .releasedAt(domain.getReleasedAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
