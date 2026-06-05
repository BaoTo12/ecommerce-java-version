package com.ecommerce.monolith.domain.payment.mapper;

import com.ecommerce.monolith.domain.payment.dto.PaymentResponse;
import com.ecommerce.monolith.domain.payment.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);
}
