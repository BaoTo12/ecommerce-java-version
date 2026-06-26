package com.ecommerce.domain.order.domain.ports.in;

import com.ecommerce.domain.order.domain.service.CheckoutActionService;
import com.ecommerce.domain.order.dto.CheckoutExecuteRequest;

import java.util.UUID;

public interface CheckoutActionUseCase {
    CheckoutActionService.PrepareResult prepareCheckout(UUID idempotencyKey, CheckoutExecuteRequest request);
    void finalizeCheckout(UUID idempotencyKey, UUID orderId, boolean paymentSuccess, String paymentResultJson, String failureReason);
}
