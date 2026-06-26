package com.ecommerce.domain.order.mapper;

import com.ecommerce.domain.order.dto.OrderResponse;
import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.order.domain.model.OrderItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderResponse toResponse(Order order);

    OrderResponse.OrderItemDto toOrderItemDto(OrderItem item);
}
