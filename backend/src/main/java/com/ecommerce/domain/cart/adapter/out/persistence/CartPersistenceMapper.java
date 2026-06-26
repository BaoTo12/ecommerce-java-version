package com.ecommerce.domain.cart.adapter.out.persistence;

import com.ecommerce.domain.cart.domain.model.Cart;
import com.ecommerce.domain.cart.domain.model.CartItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CartPersistenceMapper {

    public static Cart toDomain(CartJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        Cart cart = Cart.builder()
                .id(jpa.getId())
                .userId(jpa.getUserId())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .items(new ArrayList<>())
                .build();
        if (jpa.getItems() != null) {
            List<CartItem> domainItems = jpa.getItems().stream()
                    .map(itemJpa -> CartItem.builder()
                            .id(itemJpa.getId())
                            .productId(itemJpa.getProductId())
                            .productName(itemJpa.getProductName())
                            .quantity(itemJpa.getQuantity())
                            .priceSnapshot(itemJpa.getPriceSnapshot())
                            .addedAt(itemJpa.getAddedAt())
                            .selected(itemJpa.isSelected())
                            .build())
                    .collect(Collectors.toList());
            cart.setItems(domainItems);
        }
        return cart;
    }

    public static CartJpaEntity toJpa(Cart domain) {
        if (domain == null) {
            return null;
        }
        CartJpaEntity jpa = CartJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .items(new ArrayList<>())
                .build();
        if (domain.getItems() != null) {
            List<CartItemJpaEntity> jpaItems = domain.getItems().stream()
                    .map(itemDomain -> CartItemJpaEntity.builder()
                            .id(itemDomain.getId())
                            .cart(jpa)
                            .productId(itemDomain.getProductId())
                            .productName(itemDomain.getProductName())
                            .quantity(itemDomain.getQuantity())
                            .priceSnapshot(itemDomain.getPriceSnapshot())
                            .addedAt(itemDomain.getAddedAt())
                            .selected(itemDomain.isSelected())
                            .build())
                    .collect(Collectors.toList());
            jpa.setItems(jpaItems);
        }
        return jpa;
    }
}
