package com.ecommerce.monolith.domain.order.dto;

public record CheckoutExecuteRequest(
        String cardNumber, String cvc, String cardName, String expiry, String strategy) {
}
