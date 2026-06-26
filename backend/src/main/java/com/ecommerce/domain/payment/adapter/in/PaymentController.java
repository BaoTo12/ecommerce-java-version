package com.ecommerce.domain.payment.adapter.in;

import com.ecommerce.domain.payment.domain.ports.in.PaymentUseCase;
import com.ecommerce.domain.payment.dto.PaymentResponse;
import com.ecommerce.domain.payment.dto.RefundRequest;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentUseCase paymentUseCase;

    public PaymentController(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @PostMapping("/order/{orderId}/refund")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID orderId, @Valid @RequestBody RefundRequest req) {
        return ResponseEntity.ok(paymentUseCase.refundPayment(orderId, req));
    }
}
