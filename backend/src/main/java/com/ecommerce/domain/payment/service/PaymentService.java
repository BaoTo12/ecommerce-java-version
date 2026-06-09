package com.ecommerce.domain.payment.service;

import com.ecommerce.common.exception.BusinessRuleViolationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.status.OrderStatus;
import com.ecommerce.domain.notification.service.NotificationService;
import com.ecommerce.domain.order.repository.OrderRepository;
import com.ecommerce.domain.payment.dto.PaymentDetailsDto;
import com.ecommerce.domain.payment.dto.PaymentResponse;
import com.ecommerce.domain.payment.dto.RefundRequest;
import com.ecommerce.domain.payment.entity.Payment;
import com.ecommerce.domain.payment.gateway.MockPaymentGatewayClient;
import com.ecommerce.domain.payment.repository.PaymentRepository;
import com.ecommerce.domain.payment.mapper.PaymentMapper;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final MockPaymentGatewayClient gateway;
    private final NotificationService notificationService;
    private final PaymentMapper paymentMapper;

    public PaymentService(
            PaymentRepository paymentRepo,
            OrderRepository orderRepo,
            MockPaymentGatewayClient gateway,
            NotificationService notificationService,
            PaymentMapper paymentMapper) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.paymentMapper = paymentMapper;
    }

    /**
     * Process payment for an order.
     *
     * <p>Edge Case #3: SELECT FOR UPDATE on payment row (findByOrderIdForUpdate). Prevents two
     * concurrent calls from both seeing "PENDING" and charging twice.
     *
     * <p>Edge Case #9: DB UNIQUE constraint on order_id as last-resort guard. If somehow two new
     * PaymentEntity rows are created concurrently, the second INSERT will fail with
     * DataIntegrityViolationException.
     */
    public PaymentResponse processPayment(UUID orderId, UUID userId, BigDecimal amount) {
        return processPayment(
                new PaymentDetailsDto(orderId, userId, amount, null, null, null, null, null));
    }

    public PaymentResponse processPayment(PaymentDetailsDto dto) {
        UUID orderId = dto.orderId();
        UUID userId = dto.userId();
        BigDecimal amount = dto.amount();
        String cardNumber = dto.cardNumber();
        String cvc = dto.cvc();
        String cardName = dto.cardName();
        String expiry = dto.expiry();
        String strategy = dto.strategy();
        // ─── Edge Case #3: Pessimistic lock ─────────────────────────────────
        // If no existing payment, create one. If one exists, load it with lock.
        Payment payment;
        try {
            payment =
                    paymentRepo
                            .findByOrderIdForUpdate(orderId)
                            .orElseGet(
                                    () -> {
                                        Payment p = paymentRepo.saveAndFlush(
                                                Payment.builder()
                                                        .orderId(orderId)
                                                        .userId(userId)
                                                        .amount(amount)
                                                        .build());
                                        log.info("[DATABASE CHANGE] Created new Payment={} for Order={} with amount={}", p.getId(), orderId, amount);
                                        return p;
                                    });
        } catch (DataIntegrityViolationException e) {
            // Edge Case #9: Race condition → another thread just created it
            log.warn("Race condition on payment creation for order={}. Loading existing.", orderId);
            payment =
                    paymentRepo
                            .findByOrderId(orderId)
                            .orElseThrow(() -> new RuntimeException("Payment not found after conflict"));
        }
        // ────────────────────────────────────────────────────────────────────

        // Edge Case #9: Already processed → idempotent return
        if (!"PENDING".equals(payment.getStatus())) {
            log.info("Payment already processed for order={}, status={}", orderId, payment.getStatus());
            return paymentMapper.toResponse(payment);
        }

        // Call mock payment gateway
        MockPaymentGatewayClient.GatewayResponse result =
                gateway.charge(orderId, userId, amount, cardNumber, cvc, cardName, expiry, strategy);

        if (result.success()) {
            payment.markCharged();
            paymentRepo.save(payment);
            log.info("[DATABASE CHANGE] Updated Payment={} status to CHARGED", payment.getId());

            // Update order status via state machine (Edge Case #8)
            orderRepo
                    .findById(orderId)
                    .ifPresent(
                            order -> {
                                order.transitionTo(OrderStatus.PAID);
                                orderRepo.save(order);
                                log.info("[DATABASE CHANGE] Transitioned Order={} status to PAID", orderId);
                            });

            // Send order completion notification directly (Edge Case #11 / Monolith simplified)
            try {
                notificationService.sendOrderCompleted(orderId, userId);
            } catch (Exception e) {
                log.error("Failed to send order completion notification for order={}", orderId, e);
            }

            log.info("Payment succeeded: order={}, txn={}", orderId, result.transactionId());
        } else {
            payment.markFailed(result.failureReason());
            paymentRepo.save(payment);
            log.info("[DATABASE CHANGE] Updated Payment={} status to FAILED (reason: {})", payment.getId(), result.failureReason());

            orderRepo
                    .findById(orderId)
                    .ifPresent(
                            order -> {
                                order.transitionTo(OrderStatus.PAYMENT_FAILED);
                                orderRepo.save(order);
                                log.info("[DATABASE CHANGE] Transitioned Order={} status to PAYMENT_FAILED", orderId);
                            });

            // Send payment failure notification directly (Edge Case #11 / Monolith simplified)
            try {
                notificationService.sendPaymentFailed(orderId, userId, result.failureReason());
            } catch (Exception e) {
                log.error("Failed to send payment failure notification for order={}", orderId, e);
            }

            log.warn("Payment failed: order={}, reason={}", orderId, result.failureReason());
        }

        return paymentMapper.toResponse(payment);
    }

    // Edge Case #10 — Refund State Machine + Idempotency:
    //
    // 1. Load with SELECT FOR UPDATE (prevents concurrent refund processing) 2. Check status is
    // CHARGED (state machine validation) 3. Call gateway refund 4. Terminal states: REFUNDED /
    // REFUND_FAILED cannot be refunded again
    //
    // Second call with same orderId: sees status = REFUNDED → returns early. This prevents double
    // refunds even if the webhook fires twice.
    public PaymentResponse refund(UUID orderId, RefundRequest req) {
        // ─── Edge Case #3: Pessimistic lock during refund ────────────────────
        Payment payment =
                paymentRepo
                        .findByOrderIdForUpdate(orderId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));
        // ────────────────────────────────────────────────────────────────────

        // ─── Edge Case #10: State machine — only CHARGED can be refunded ─────
        if ("REFUNDED".equals(payment.getStatus())) {
            log.info("Refund already completed for order={}", orderId);
            return paymentMapper.toResponse(payment);
        }
        if ("REFUND_REQUESTED".equals(payment.getStatus())) {
            log.info("Refund already in progress for order={}", orderId);
            return paymentMapper.toResponse(payment);
        }
        if (!"CHARGED".equals(payment.getStatus())) {
            throw new BusinessRuleViolationException(
                    "Cannot refund payment in status: "
                            + payment.getStatus()
                            + ". Only CHARGED payments can be refunded.");
        }
        // ────────────────────────────────────────────────────────────────────

        payment.requestRefund(req.reason());

        MockPaymentGatewayClient.GatewayResponse result = gateway.refund(payment.getAmount());

        if (result.success()) {
            payment.markRefunded();
            paymentRepo.save(payment);
            log.info("[DATABASE CHANGE] Updated Payment={} status to REFUNDED", payment.getId());

            orderRepo
                    .findById(orderId)
                    .ifPresent(
                            order -> {
                                if (order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
                                    order.transitionTo(OrderStatus.CANCELLED);
                                    orderRepo.save(order);
                                    log.info("[DATABASE CHANGE] Transitioned Order={} status to CANCELLED (refunded)", orderId);
                                }
                            });

            log.info("Refund succeeded: order={}", orderId);
        } else {
            payment.markRefundFailed(result.failureReason());
            paymentRepo.save(payment);
            log.info("[DATABASE CHANGE] Updated Payment={} status to REFUND_FAILED (reason: {})", payment.getId(), result.failureReason());
            log.error(
                    "Refund FAILED — MANUAL INTERVENTION REQUIRED: order={}, reason={}",
                    orderId,
                    result.failureReason());
        }

        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(UUID orderId) {
        return paymentRepo
                .findByOrderId(orderId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));
    }


}
