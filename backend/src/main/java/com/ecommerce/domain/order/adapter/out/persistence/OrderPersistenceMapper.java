package com.ecommerce.domain.order.adapter.out.persistence;

import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.order.domain.model.OrderItem;
import com.ecommerce.domain.order.domain.model.CheckoutSession;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OrderPersistenceMapper {

    public static Order toDomain(OrderJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        Order order = Order.builder()
                .id(jpa.getId())
                .userId(jpa.getUserId())
                .status(jpa.getStatus())
                .totalAmount(jpa.getTotalAmount())
                .idempotencyKey(jpa.getIdempotencyKey())
                .shippingAddressId(jpa.getShippingAddressId())
                .notes(jpa.getNotes())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .items(new ArrayList<>())
                .build();
        if (jpa.getItems() != null) {
            List<OrderItem> domainItems = jpa.getItems().stream()
                    .map(itemJpa -> OrderItem.builder()
                            .id(itemJpa.getId())
                            .productId(itemJpa.getProductId())
                            .productName(itemJpa.getProductName())
                            .quantity(itemJpa.getQuantity())
                            .createdAt(itemJpa.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            order.setItems(domainItems);
        }
        return order;
    }

    public static OrderJpaEntity toJpa(Order domain) {
        if (domain == null) {
            return null;
        }
        OrderJpaEntity jpa = OrderJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .status(domain.getStatus())
                .totalAmount(domain.getTotalAmount())
                .idempotencyKey(domain.getIdempotencyKey())
                .shippingAddressId(domain.getShippingAddressId())
                .notes(domain.getNotes())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .items(new ArrayList<>())
                .build();
        if (domain.getItems() != null) {
            List<OrderItemJpaEntity> jpaItems = domain.getItems().stream()
                    .map(itemDomain -> OrderItemJpaEntity.builder()
                            .id(itemDomain.getId())
                            .order(jpa)
                            .productId(itemDomain.getProductId())
                            .productName(itemDomain.getProductName())
                            .quantity(itemDomain.getQuantity())
                            .createdAt(itemDomain.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            jpa.setItems(jpaItems);
        }
        return jpa;
    }

    public static CheckoutSession toDomain(CheckoutSessionJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return CheckoutSession.builder()
                .idempotencyKey(jpa.getIdempotencyKey())
                .userId(jpa.getUserId())
                .cartId(jpa.getCartId())
                .orderId(jpa.getOrderId())
                .totalAmount(jpa.getTotalAmount())
                .status(jpa.getStatus())
                .responseBody(jpa.getResponseBody())
                .createdAt(jpa.getCreatedAt())
                .expiresAt(jpa.getExpiresAt())
                .build();
    }

    public static CheckoutSessionJpaEntity toJpa(CheckoutSession domain) {
        if (domain == null) {
            return null;
        }
        return CheckoutSessionJpaEntity.builder()
                .idempotencyKey(domain.getIdempotencyKey())
                .userId(domain.getUserId())
                .cartId(domain.getCartId())
                .orderId(domain.getOrderId())
                .totalAmount(domain.getTotalAmount())
                .status(domain.getStatus())
                .responseBody(domain.getResponseBody())
                .createdAt(domain.getCreatedAt())
                .expiresAt(domain.getExpiresAt())
                .build();
    }
}
