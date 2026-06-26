package com.ecommerce.domain.order.mapper;

import com.ecommerce.domain.order.dto.CheckoutSessionResponse;
import com.ecommerce.domain.order.domain.model.CheckoutSession;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CheckoutMapper {
    CheckoutSessionResponse toCheckoutSessionResponse(CheckoutSession checkoutSession);
}
