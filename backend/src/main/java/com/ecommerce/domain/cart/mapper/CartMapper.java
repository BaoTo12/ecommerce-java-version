package com.ecommerce.domain.cart.mapper;

import com.ecommerce.domain.cart.dto.CartResponse;
import com.ecommerce.domain.cart.domain.model.Cart;
import com.ecommerce.domain.cart.domain.model.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(target = "cartId", source = "id")
    @Mapping(target = "totalAmount", expression = "java(calculateTotal(cart))")
    CartResponse toResponse(Cart cart);

    @Mapping(target = "itemId", source = "id")
    @Mapping(target = "unitPrice", source = "priceSnapshot")
    @Mapping(target = "subtotal", expression = "java(item.getPriceSnapshot().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    CartResponse.CartItemDto toCartItemDto(CartItem item);

    default BigDecimal calculateTotal(Cart cart) {
        if (cart == null || cart.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return cart.getItems().stream()
            .filter(CartItem::isSelected)
            .map(i -> i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
