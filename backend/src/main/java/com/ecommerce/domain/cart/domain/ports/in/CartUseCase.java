package com.ecommerce.domain.cart.domain.ports.in;

import com.ecommerce.domain.cart.dto.AddToCartRequest;
import com.ecommerce.domain.cart.dto.CartResponse;
import java.util.UUID;

public interface CartUseCase {
    CartResponse getCart();
    CartResponse addItem(AddToCartRequest req);
    CartResponse updateItem(UUID itemId, int newQuantity);
    CartResponse removeItem(UUID itemId);
}
