package com.ecommerce.order.service;

import com.ecommerce.order.model.entity.OrderEntity;
import com.ecommerce.order.model.entity.OrderReadModelEntity;
import com.ecommerce.order.model.enums.OrderStatus;
import com.ecommerce.order.repository.OrderReadModelRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.shared.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/**
 * Saga orchestrator — reacts to events from Inventory and Payment services,
 * advances order state and publishes compensating events.
 */
@Service
@Transactional
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final OutboxService outboxService;
    private final OrderReadModelRepository readModelRepo;

    public OrderSagaService(OrderRepository orderRepo, OrderService orderService,
                            OutboxService outboxService, OrderReadModelRepository readModelRepo) {
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.outboxService = outboxService;
        this.readModelRepo = readModelRepo;
    }

    /**
     * Handle inventory reservation success → request payment
     */
    public void handleInventoryReserved(InventoryReservedEvent event) {
        UUID orderId = event.orderId();
        MDC.put("orderId", orderId.toString());

        OrderEntity order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("Order {} not in CONFIRMED state (current={}), skipping", orderId, order.getStatus());
            return;
        }

        OrderStatus from = order.getStatus();
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
        orderService.recordTransition(orderId, from, OrderStatus.PAYMENT_PROCESSING, "Inventory reserved, payment initiated");
        orderRepo.save(order);

        // Publish PaymentRequestedEvent
        PaymentRequestedEvent paymentEvent = new PaymentRequestedEvent(
                orderId, order.getUserId(), order.getTotalAmount(), Instant.now());
        outboxService.saveEvent("PaymentRequestedEvent", orderId, KafkaTopics.PAYMENT_REQUESTED, paymentEvent);

        updateReadModel(order);
        log.info("Inventory reserved, payment requested: orderId={}", orderId);
    }

    /**
     * Handle inventory reservation failure → cancel order
     */
    public void handleInventoryFailed(InventoryFailedEvent event) {
        UUID orderId = event.orderId();
        MDC.put("orderId", orderId.toString());

        OrderEntity order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus().isTerminal()) {
            log.warn("Order {} already terminal, skipping", orderId);
            return;
        }

        OrderStatus from = order.getStatus();
        order.transitionTo(OrderStatus.CANCELLED);
        orderService.recordTransition(orderId, from, OrderStatus.CANCELLED, "Insufficient stock: " + event.reason());
        orderRepo.save(order);

        OrderCancelledEvent cancelEvent = new OrderCancelledEvent(
                orderId, order.getUserId(), "Insufficient stock", Instant.now());
        outboxService.saveEvent("OrderCancelledEvent", orderId, KafkaTopics.ORDERS_CANCELLED, cancelEvent);

        updateReadModel(order);
        log.info("Order cancelled due to inventory failure: orderId={}", orderId);
    }

    /**
     * Handle payment success → complete order
     */
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        UUID orderId = event.orderId();
        MDC.put("orderId", orderId.toString());

        OrderEntity order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            log.warn("Order {} not in PAYMENT_PROCESSING (current={}), skipping", orderId, order.getStatus());
            return;
        }

        // PAYMENT_PROCESSING → PAID → FULFILLING → COMPLETED
        order.transitionTo(OrderStatus.PAID);
        orderService.recordTransition(orderId, OrderStatus.PAYMENT_PROCESSING, OrderStatus.PAID, "Payment successful");

        order.transitionTo(OrderStatus.FULFILLING);
        orderService.recordTransition(orderId, OrderStatus.PAID, OrderStatus.FULFILLING, "Fulfillment started");

        order.transitionTo(OrderStatus.COMPLETED);
        orderService.recordTransition(orderId, OrderStatus.FULFILLING, OrderStatus.COMPLETED, "Order fulfilled");

        orderRepo.save(order);

        OrderStatusChangedEvent statusEvent = new OrderStatusChangedEvent(
                orderId, order.getUserId(), "PAYMENT_PROCESSING", "COMPLETED", "Order completed", Instant.now());
        outboxService.saveEvent("OrderStatusChangedEvent", orderId, KafkaTopics.ORDERS_STATUS_CHANGED, statusEvent);

        updateReadModel(order, "CHARGED");
        log.info("Order completed: orderId={}", orderId);
    }

    /**
     * Handle payment failure → cancel order + release inventory
     */
    public void handlePaymentFailed(PaymentFailedEvent event) {
        UUID orderId = event.orderId();
        MDC.put("orderId", orderId.toString());

        OrderEntity order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus().isTerminal()) {
            log.warn("Order {} already terminal, skipping", orderId);
            return;
        }

        order.transitionTo(OrderStatus.PAYMENT_FAILED);
        orderService.recordTransition(orderId, OrderStatus.PAYMENT_PROCESSING, OrderStatus.PAYMENT_FAILED,
                "Payment failed: " + event.failureReason());

        order.transitionTo(OrderStatus.CANCELLED);
        orderService.recordTransition(orderId, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED,
                "Order cancelled after payment failure");
        orderRepo.save(order);

        // Compensating: release inventory
        var releaseItems = order.getItems().stream()
                .map(i -> new OrderCreatedEvent.OrderItemPayload(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        InventoryReleaseRequestedEvent releaseEvent = new InventoryReleaseRequestedEvent(orderId, releaseItems, Instant.now());
        outboxService.saveEvent("InventoryReleaseRequestedEvent", orderId,
                KafkaTopics.INVENTORY_RELEASE_REQUESTED, releaseEvent);

        OrderCancelledEvent cancelEvent = new OrderCancelledEvent(
                orderId, order.getUserId(), "Payment failed", Instant.now());
        outboxService.saveEvent("OrderCancelledEvent", orderId, KafkaTopics.ORDERS_CANCELLED, cancelEvent);

        updateReadModel(order, "FAILED");
        log.info("Order cancelled due to payment failure: orderId={}", orderId);
    }

    private void updateReadModel(OrderEntity order) {
        updateReadModel(order, null);
    }

    private void updateReadModel(OrderEntity order, String paymentStatus) {
        OrderReadModelEntity rm = readModelRepo.findById(order.getId()).orElse(null);
        if (rm == null) {
            rm = OrderReadModelEntity.create(order.getId(), order.getUserId(),
                    order.getStatus().name(), order.getTotalAmount(), order.getItems().size());
        } else {
            rm.updateStatus(order.getStatus().name());
        }
        if (paymentStatus != null) {
            rm.updatePaymentStatus(paymentStatus);
        }
        readModelRepo.save(rm);
    }
}
