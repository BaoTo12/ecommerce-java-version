package com.ecommerce.monolith.domain.order.mapper;

import com.ecommerce.monolith.domain.order.dto.OrderResponse;
import com.ecommerce.monolith.domain.order.entity.Order;
import com.ecommerce.monolith.domain.order.entity.OrderItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderResponse toResponse(Order order);

    OrderResponse.OrderItemDto toOrderItemDto(OrderItem item);
}
