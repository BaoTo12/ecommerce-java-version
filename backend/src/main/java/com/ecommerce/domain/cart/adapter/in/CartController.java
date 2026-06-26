package com.ecommerce.domain.cart.adapter.in;

import com.ecommerce.domain.cart.domain.ports.in.CartUseCase;
import com.ecommerce.domain.cart.dto.AddToCartRequest;
import com.ecommerce.domain.cart.dto.CartResponse;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartUseCase cartUseCase;

    public CartController(CartUseCase cartUseCase) {
        this.cartUseCase = cartUseCase;
    }

    @GetMapping
    public CartResponse getCart() {
        return cartUseCase.getCart();
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddToCartRequest req) {
        return cartUseCase.addItem(req);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(@PathVariable UUID itemId, @RequestParam int quantity) {
        return cartUseCase.updateItem(itemId, quantity);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable UUID itemId) {
        return ResponseEntity.ok(cartUseCase.removeItem(itemId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        // cartService.clearCart handled via checkout
        return ResponseEntity.noContent().build();
    }
}
