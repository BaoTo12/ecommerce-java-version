package com.ecommerce.domain.order.mapper;

import com.ecommerce.domain.order.dto.OrderResponse;
import com.ecommerce.domain.order.entity.Order;
import com.ecommerce.domain.order.entity.OrderItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderResponse toResponse(Order order);

    OrderResponse.OrderItemDto toOrderItemDto(OrderItem item);
}
