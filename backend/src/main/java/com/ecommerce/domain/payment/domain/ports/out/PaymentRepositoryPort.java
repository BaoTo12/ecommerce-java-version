package com.ecommerce.domain.payment.domain.ports.out;

import com.ecommerce.domain.payment.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByOrderIdForUpdate(UUID orderId);
    Payment save(Payment payment);
    Payment saveAndFlush(Payment payment);
}
