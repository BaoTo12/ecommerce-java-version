package com.ecommerce.domain.payment.domain.ports.in;

import com.ecommerce.domain.payment.dto.PaymentDetailsDto;
import com.ecommerce.domain.payment.dto.PaymentResponse;
import com.ecommerce.domain.payment.dto.RefundRequest;
import java.util.UUID;

public interface PaymentUseCase {
    PaymentResponse processPayment(PaymentDetailsDto req);
    PaymentResponse refundPayment(UUID orderId, RefundRequest req);
}
