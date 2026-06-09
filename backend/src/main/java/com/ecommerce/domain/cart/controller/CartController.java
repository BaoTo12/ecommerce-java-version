package com.ecommerce.domain.cart.controller;

import com.ecommerce.domain.cart.dto.AddToCartRequest;
import com.ecommerce.domain.cart.dto.CartResponse;
import com.ecommerce.domain.cart.service.CartService;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart() {
        return cartService.getCart();
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddToCartRequest req) {
        return cartService.addItem(req);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(@PathVariable UUID itemId, @RequestParam int quantity) {
        return cartService.updateItem(itemId, quantity);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@PathVariable UUID itemId) {
        return cartService.removeItem(itemId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        // cartService.clearCart handled via checkout
        return ResponseEntity.noContent().build();
    }
}
