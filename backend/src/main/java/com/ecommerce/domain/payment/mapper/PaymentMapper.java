package com.ecommerce.domain.payment.mapper;

import com.ecommerce.domain.payment.dto.PaymentResponse;
import com.ecommerce.domain.payment.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);
}
