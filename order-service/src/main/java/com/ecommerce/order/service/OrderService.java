package com.ecommerce.order.service;

import com.ecommerce.order.model.dto.CreateOrderRequest;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.model.dto.OrderResponse.OrderItemDto;
import com.ecommerce.order.model.dto.OrderHistoryResponse;
import com.ecommerce.order.model.entity.OrderEntity;
import com.ecommerce.order.model.entity.OrderItemEntity;
import com.ecommerce.order.model.entity.OrderStatusHistoryEntity;
import com.ecommerce.order.model.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderStatusHistoryRepository;
import com.ecommerce.shared.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

        private static final Logger log = LoggerFactory.getLogger(OrderService.class);

        private final OrderRepository orderRepo;
        private final OrderStatusHistoryRepository historyRepo;
        private final OutboxService outboxService;

        public OrderService(OrderRepository orderRepo, OrderStatusHistoryRepository historyRepo,
                        OutboxService outboxService) {
                this.orderRepo = orderRepo;
                this.historyRepo = historyRepo;
                this.outboxService = outboxService;
        }

        public OrderResponse createOrder(CreateOrderRequest req) {
                List<OrderItemEntity> items = req.items().stream()
                                .map(i -> new OrderItemEntity(i.productId(), i.quantity(), i.unitPrice()))
                                .toList();

                OrderEntity order = OrderEntity.create(req.userId(), items);
                order = orderRepo.save(order); // Save first to generate ID

                recordTransition(order.getId(), null, OrderStatus.PENDING, "Order created");

                // Immediately confirm
                order.transitionTo(OrderStatus.CONFIRMED);
                recordTransition(order.getId(), OrderStatus.PENDING, OrderStatus.CONFIRMED,
                                "Order confirmed after validation");

                orderRepo.save(order);

                // Persist OrderCreatedEvent to outbox
                List<OrderCreatedEvent.OrderItemPayload> eventItems = order.getItems().stream()
                                .map(i -> new OrderCreatedEvent.OrderItemPayload(i.getProductId(), i.getQuantity(),
                                                i.getUnitPrice()))
                                .toList();
                OrderCreatedEvent event = new OrderCreatedEvent(
                                order.getId(), order.getUserId(), eventItems, order.getTotalAmount(), Instant.now());
                outboxService.saveEvent("OrderCreatedEvent", order.getId(), KafkaTopics.ORDERS_CREATED, event);

                log.info("Order created: orderId={}, status={}", order.getId(), order.getStatus());
                return toResponse(order);
        }

        @Transactional(readOnly = true)
        public OrderResponse getOrder(UUID orderId) {
                OrderEntity order = orderRepo.findById(orderId)
                                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
                return toResponse(order);
        }

        public void cancelOrder(UUID orderId) {
                OrderEntity order = orderRepo.findById(orderId)
                                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

                OrderStatus from = order.getStatus();
                order.transitionTo(OrderStatus.CANCELLED);
                recordTransition(orderId, from, OrderStatus.CANCELLED, "Cancelled by customer");
                orderRepo.save(order);

                OrderCancelledEvent event = new OrderCancelledEvent(orderId, order.getUserId(), "Cancelled by customer",
                                Instant.now());
                outboxService.saveEvent("OrderCancelledEvent", orderId, KafkaTopics.ORDERS_CANCELLED, event);

                log.info("Order cancelled: orderId={}", orderId);
        }

        @Transactional(readOnly = true)
        public List<OrderHistoryResponse> getOrderHistory(UUID orderId) {
                return historyRepo.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                                .map(h -> new OrderHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getReason(),
                                                h.getCreatedAt()))
                                .toList();
        }

        public void recordTransition(UUID orderId, OrderStatus from, OrderStatus to, String reason) {
                String fromStr = from != null ? from.name() : null;
                historyRepo.save(OrderStatusHistoryEntity.of(orderId, fromStr, to.name(), reason));
        }

        public OrderResponse toResponse(OrderEntity order) {
                List<OrderItemDto> items = order.getItems().stream()
                                .map(i -> new OrderItemDto(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                                .toList();
                return new OrderResponse(order.getId(), order.getUserId(), order.getStatus().name(),
                                order.getTotalAmount(), items, order.getCreatedAt(), order.getUpdatedAt());
        }
}
