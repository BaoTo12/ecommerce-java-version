package com.ecommerce.domain.order.domain.ports.in;

import com.ecommerce.domain.order.dto.CheckoutExecuteRequest;
import com.ecommerce.domain.order.dto.CheckoutSessionRequest;
import com.ecommerce.domain.order.dto.CheckoutSessionResponse;
import com.ecommerce.domain.order.dto.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderUseCase {
    String checkout(UUID idempotencyKey, CheckoutExecuteRequest request);
    CheckoutSessionResponse checkoutSession(CheckoutSessionRequest request);
    OrderResponse getOrder(UUID orderId);
    Page<OrderResponse> listOrders(Pageable pageable);
    void cancelOrder(UUID orderId);
}
