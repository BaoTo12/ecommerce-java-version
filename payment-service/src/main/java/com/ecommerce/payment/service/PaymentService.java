package com.ecommerce.payment.service;

import com.ecommerce.payment.integration.gateway.MockPaymentGatewayClient;
import com.ecommerce.payment.integration.gateway.MockPaymentGatewayClient.GatewayResponse;
import com.ecommerce.payment.model.dto.PaymentResponse;
import com.ecommerce.payment.model.entity.PaymentEntity;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.shared.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepo;
    private final MockPaymentGatewayClient gateway;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepo, MockPaymentGatewayClient gateway,
                          KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.paymentRepo = paymentRepo;
        this.gateway = gateway;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void processPayment(PaymentRequestedEvent event) {
        UUID orderId = event.orderId();

        // Idempotency check at DB level — UNIQUE on order_id
        if (paymentRepo.existsByOrderId(orderId)) {
            log.warn("Payment already exists for order={}, skipping", orderId);
            return;
        }

        PaymentEntity payment = PaymentEntity.create(orderId, event.userId(), event.amount());

        // Call mock gateway
        GatewayResponse result = gateway.charge(orderId, event.userId(), event.amount());

        if (result.success()) {
            payment.markCharged(result.transactionId());
            paymentRepo.save(payment);

            publishEvent(KafkaTopics.PAYMENT_SUCCEEDED, orderId.toString(),
                    new PaymentSucceededEvent(orderId, event.userId(), event.amount(),
                            result.transactionId(), Instant.now()));
            log.info("Payment succeeded: orderId={}, txnId={}", orderId, result.transactionId());
        } else {
            payment.markFailed(result.failureReason());
            paymentRepo.save(payment);

            publishEvent(KafkaTopics.PAYMENT_FAILED, orderId.toString(),
                    new PaymentFailedEvent(orderId, event.userId(), event.amount(),
                            result.failureReason(), Instant.now()));
            log.warn("Payment failed: orderId={}, reason={}", orderId, result.failureReason());
        }
    }

    public void processRefund(PaymentRefundRequestedEvent event) {
        UUID orderId = event.orderId();

        PaymentEntity payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));

        if (!"CHARGED".equals(payment.getStatus())) {
            log.warn("Payment for order={} not in CHARGED state, skipping refund", orderId);
            return;
        }

        payment.markRefundRequested(event.reason());

        GatewayResponse result = gateway.refund(payment.getGatewayTransactionId(), payment.getAmount());

        if (result.success()) {
            payment.markRefunded();
            paymentRepo.save(payment);

            publishEvent(KafkaTopics.PAYMENT_REFUNDED, orderId.toString(),
                    new PaymentRefundedEvent(orderId, event.userId(), event.amount(), Instant.now()));
            log.info("Refund succeeded: orderId={}", orderId);
        } else {
            payment.markRefundFailed(result.failureReason());
            paymentRepo.save(payment);
            log.error("Refund failed: orderId={} — REQUIRES MANUAL INTERVENTION", orderId);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(UUID orderId) {
        PaymentEntity p = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
        return new PaymentResponse(p.getOrderId(), p.getStatus(), p.getAmount(),
                p.getGatewayTransactionId(), p.getCreatedAt());
    }

    private void publishEvent(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish event to topic={}", topic, e);
        }
    }
}
