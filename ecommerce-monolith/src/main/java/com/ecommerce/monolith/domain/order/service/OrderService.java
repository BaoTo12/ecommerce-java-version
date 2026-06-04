package com.ecommerce.monolith.domain.order.service;

import com.ecommerce.monolith.domain.order.dto.OrderHistoryResponse;
import com.ecommerce.monolith.domain.order.dto.OrderResponse;
import com.ecommerce.monolith.domain.order.entity.OrderEntity;
import com.ecommerce.monolith.domain.order.entity.OrderStatusHistoryEntity;
import com.ecommerce.monolith.domain.order.enums.OrderStatus;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.order.repository.OrderStatusHistoryRepository;
import com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.infrastructure.exception.ResourceNotFoundException;
import com.ecommerce.monolith.domain.notification.service.NotificationService;
import com.ecommerce.monolith.infrastructure.security.SecurityUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order management service (post-creation operations).
 *
 * <p>Edge Case #8 — State machine: cancellation goes through transitionTo() Edge Case #13 —
 * Read-after-write: listOrders returns @Transactional(readOnly=true) ensuring consistent snapshot
 * reads
 */
@Service
@Transactional
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository orderRepo;
  private final OrderStatusHistoryRepository historyRepo;
  private final NotificationService notificationService;

  public OrderService(
      OrderRepository orderRepo,
      OrderStatusHistoryRepository historyRepo,
      NotificationService notificationService) {
    this.orderRepo = orderRepo;
    this.historyRepo = historyRepo;
    this.notificationService = notificationService;
  }

  /**
   * Edge Case #13 — Read-after-Write Consistency: @Transactional(readOnly=true) tells Hibernate to
   * use the read-replica (if configured) but with snapshot isolation, ensuring consistent reads.
   * The caller who just wrote will see their own data (same connection).
   */
  @Transactional(readOnly = true)
  public OrderResponse getOrder(UUID orderId) {
    UUID userId = SecurityUtils.getCurrentUserId();
    OrderEntity order =
        orderRepo
            .findById(orderId)
            .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

    if (!order.getUserId().equals(userId)) {
      throw new BusinessRuleViolationException("Order does not belong to you");
    }
    return toResponse(order);
  }

  @Transactional(readOnly = true)
  public Page<OrderResponse> listOrders(Pageable pageable) {
    UUID userId = SecurityUtils.getCurrentUserId();
    return orderRepo.findByUserId(userId, pageable).map(this::toResponse);
  }

  /**
   * Edge Case #8 — State Machine: ONLY valid cancellations are allowed. E.g., COMPLETED → CANCELLED
   * is rejected with HTTP 409.
   */
  public void cancelOrder(UUID orderId) {
    UUID userId = SecurityUtils.getCurrentUserId();
    OrderEntity order =
        orderRepo
            .findById(orderId)
            .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

    if (!order.getUserId().equals(userId)) {
      throw new BusinessRuleViolationException("Order does not belong to you");
    }

    OrderStatus from = order.getStatus();
    order.transitionTo(OrderStatus.CANCELLED); // Edge Case #8: validated
    orderRepo.save(order);

    historyRepo.save(
        OrderStatusHistoryEntity.of(
            orderId, from.name(), OrderStatus.CANCELLED.name(), "Cancelled by customer"));

    // Send cancellation notification directly (Edge Case #11 / Monolith simplified)
    try {
      notificationService.sendOrderCancelled(orderId, userId, "Cancelled by customer");
    } catch (Exception e) {
      log.error("Failed to send order cancellation notification for order={}", orderId, e);
    }

    log.info("Order cancelled: orderId={}", orderId);
  }

  @Transactional(readOnly = true)
  public List<OrderHistoryResponse> getOrderHistory(UUID orderId) {
    return historyRepo.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
        .map(
            h ->
                new OrderHistoryResponse(
                    h.getFromStatus(), h.getToStatus(), h.getReason(), h.getCreatedAt()))
        .toList();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private OrderResponse toResponse(OrderEntity o) {
    List<OrderResponse.OrderItemDto> items =
        o.getItems().stream()
            .map(
                i ->
                    new OrderResponse.OrderItemDto(
                        i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
            .toList();
    return new OrderResponse(
        o.getId(),
        o.getUserId(),
        o.getStatus().name(),
        o.getTotalAmount(),
        items,
        o.getCreatedAt(),
        o.getUpdatedAt());
  }
}
