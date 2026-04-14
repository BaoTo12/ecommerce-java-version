package com.ecommerce.payment.controller;

import com.ecommerce.payment.model.dto.PaymentResponse;
import com.ecommerce.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId));
    }
}
