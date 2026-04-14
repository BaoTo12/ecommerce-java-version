package com.ecommerce.order.controller;

import com.ecommerce.order.model.dto.CheckoutRequest;
import com.ecommerce.order.model.dto.CheckoutResponse;
import com.ecommerce.order.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest req) {
        return ResponseEntity.ok(checkoutService.checkout(req));
    }
}
