package com.ecommerce.monolith.domain.order.mapper;

import com.ecommerce.monolith.domain.order.dto.CheckoutSessionResponse;
import com.ecommerce.monolith.domain.order.entity.CheckoutSession;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CheckoutMapper {
  CheckoutSessionResponse toCheckoutSessionResponse(CheckoutSession checkoutSession);
}
