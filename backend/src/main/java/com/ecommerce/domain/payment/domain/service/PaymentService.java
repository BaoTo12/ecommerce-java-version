package com.ecommerce.domain.payment.domain.service;

import com.ecommerce.common.exception.BusinessRuleViolationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.status.OrderStatus;
import com.ecommerce.domain.notification.domain.ports.in.NotificationUseCase;
import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.order.domain.ports.out.OrderRepositoryPort;
import com.ecommerce.domain.payment.domain.model.DuplicatePaymentException;
import com.ecommerce.domain.payment.domain.model.Payment;
import com.ecommerce.domain.payment.domain.ports.in.PaymentUseCase;
import com.ecommerce.domain.payment.domain.ports.out.PaymentGatewayPort;
import com.ecommerce.domain.payment.domain.ports.out.PaymentRepositoryPort;
import com.ecommerce.domain.payment.dto.PaymentDetailsDto;
import com.ecommerce.domain.payment.dto.PaymentResponse;
import com.ecommerce.domain.payment.dto.RefundRequest;
import com.ecommerce.domain.payment.mapper.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentService implements PaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepositoryPort paymentRepo;
    private final OrderRepositoryPort orderRepo;
    private final PaymentGatewayPort gateway;
    private final NotificationUseCase notificationUseCase;
    private final PaymentMapper paymentMapper;

    public PaymentService(
            PaymentRepositoryPort paymentRepo,
            OrderRepositoryPort orderRepo,
            PaymentGatewayPort gateway,
            NotificationUseCase notificationUseCase,
            PaymentMapper paymentMapper) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.gateway = gateway;
        this.notificationUseCase = notificationUseCase;
        this.paymentMapper = paymentMapper;
    }

    public PaymentResponse processPayment(UUID orderId, UUID userId, BigDecimal amount) {
        return processPayment(
                new PaymentDetailsDto(orderId, userId, amount, null, null, null, null, null));
    }

    @Override
    public PaymentResponse processPayment(PaymentDetailsDto dto) {
        UUID orderId = dto.orderId();
        UUID userId = dto.userId();
        BigDecimal amount = dto.amount();
        String cardNumber = dto.cardNumber();
        String cvc = dto.cvc();
        String cardName = dto.cardName();
        String expiry = dto.expiry();
        String strategy = dto.strategy();

        Payment payment;
        try {
            payment = paymentRepo
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
        } catch (DuplicatePaymentException e) {
            log.warn("Race condition on payment creation for order={}. Loading existing.", orderId);
            payment = paymentRepo
                    .findByOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment not found after conflict"));
        }

        if (!"PENDING".equals(payment.getStatus())) {
            log.info("Payment already processed for order={}, status={}", orderId, payment.getStatus());
            return paymentMapper.toResponse(payment);
        }

        PaymentGatewayPort.GatewayResponse result = gateway.charge(
                orderId, userId, amount, cardNumber, cvc, cardName, expiry, strategy);

        if (result.success()) {
            payment.markCharged();
            paymentRepo.save(payment);
            log.info("[DATABASE CHANGE] Updated Payment={} status to CHARGED", payment.getId());

            orderRepo.findById(orderId).ifPresent(order -> {
                order.transitionTo(OrderStatus.PAID);
                orderRepo.save(order);
                log.info("[DATABASE CHANGE] Transitioned Order={} status to PAID", orderId);
            });

            try {
                notificationUseCase.sendOrderCompleted(orderId, userId);
            } catch (Exception e) {
                log.error("Failed to send order completion notification for order={}", orderId, e);
            }

            log.info("Payment succeeded: order={}, txn={}", orderId, result.transactionId());
        } else {
            payment.markFailed(result.failureReason());
            paymentRepo.save(payment);
            log.info("[DATABASE CHANGE] Updated Payment={} status to FAILED (reason: {})", payment.getId(), result.failureReason());

            orderRepo.findById(orderId).ifPresent(order -> {
                order.transitionTo(OrderStatus.PAYMENT_FAILED);
                orderRepo.save(order);
                log.info("[DATABASE CHANGE] Transitioned Order={} status to PAYMENT_FAILED", orderId);
            });

            try {
                notificationUseCase.sendPaymentFailed(orderId, userId, result.failureReason());
            } catch (Exception e) {
                log.error("Failed to send payment failure notification for order={}", orderId, e);
            }

            log.warn("Payment failed: order={}, reason={}", orderId, result.failureReason());
        }

        return paymentMapper.toResponse(payment);
    }

    @Override
    public PaymentResponse refundPayment(UUID orderId, RefundRequest req) {
        Payment payment = paymentRepo
                .findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));

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

        payment.requestRefund(req.reason());

        PaymentGatewayPort.GatewayResponse result = gateway.refund(payment.getAmount());

        if (result.success()) {
            payment.markRefunded();
            paymentRepo.save(payment);
            log.info("[DATABASE CHANGE] Updated Payment={} status to REFUNDED", payment.getId());

            orderRepo.findById(orderId).ifPresent(order -> {
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
}
