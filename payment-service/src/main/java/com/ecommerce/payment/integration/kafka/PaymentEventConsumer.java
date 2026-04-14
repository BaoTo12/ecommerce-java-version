package com.ecommerce.payment.integration.kafka;

import com.ecommerce.payment.service.PaymentService;
import com.ecommerce.shared.event.KafkaTopics;
import com.ecommerce.shared.event.PaymentRefundRequestedEvent;
import com.ecommerce.shared.event.PaymentRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Collections;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final Set<String> processedEvents = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PaymentEventConsumer(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_REQUESTED, groupId = "payment-service")
    public void onPaymentRequested(String message) {
        try {
            PaymentRequestedEvent event = objectMapper.readValue(message, PaymentRequestedEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            String key = "payment:payment-requested:" + orderId;
            if (!processedEvents.add(key)) {
                log.warn("Duplicate PaymentRequestedEvent skipped: {}", orderId);
                return;
            }

            paymentService.processPayment(event);
        } catch (Exception e) {
            log.error("Failed to process PaymentRequestedEvent", e);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_REFUND_REQUESTED, groupId = "payment-service")
    public void onRefundRequested(String message) {
        try {
            PaymentRefundRequestedEvent event = objectMapper.readValue(message, PaymentRefundRequestedEvent.class);
            String orderId = event.orderId().toString();
            MDC.put("orderId", orderId);

            String key = "payment:refund-requested:" + orderId;
            if (!processedEvents.add(key)) {
                log.warn("Duplicate PaymentRefundRequestedEvent skipped: {}", orderId);
                return;
            }

            paymentService.processRefund(event);
        } catch (Exception e) {
            log.error("Failed to process PaymentRefundRequestedEvent", e);
        } finally {
            MDC.clear();
        }
    }
}
