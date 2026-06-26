package com.ecommerce.domain.payment.adapter.out.persistence;

import com.ecommerce.domain.payment.domain.model.Payment;

public class PaymentPersistenceMapper {

    public static Payment toDomain(PaymentJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return Payment.builder()
                .id(jpa.getId())
                .orderId(jpa.getOrderId())
                .userId(jpa.getUserId())
                .amount(jpa.getAmount())
                .status(jpa.getStatus())
                .failureReason(jpa.getFailureReason())
                .refundReason(jpa.getRefundReason())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .build();
    }

    public static PaymentJpaEntity toJpa(Payment domain) {
        if (domain == null) {
            return null;
        }
        return PaymentJpaEntity.builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .userId(domain.getUserId())
                .amount(domain.getAmount())
                .status(domain.getStatus())
                .failureReason(domain.getFailureReason())
                .refundReason(domain.getRefundReason())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
