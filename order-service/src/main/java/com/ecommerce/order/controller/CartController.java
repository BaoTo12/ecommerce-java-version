package com.ecommerce.order.controller;

import com.ecommerce.order.model.dto.AddCartItemRequest;
import com.ecommerce.order.model.dto.CartResponse;
import com.ecommerce.order.model.dto.UpdateCartItemRequest;
import com.ecommerce.order.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest req) {
        return ResponseEntity.ok(cartService.addItem(req));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(@PathVariable UUID itemId,
                                                    @Valid @RequestBody UpdateCartItemRequest req) {
        return ResponseEntity.ok(cartService.updateItem(itemId, req.quantity()));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID itemId) {
        cartService.removeItem(itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestParam UUID userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }
}
