package com.ecommerce.monolith.domain.order.service;

import com.ecommerce.monolith.common.exception.cart.CartEmptyException;
import com.ecommerce.monolith.common.exception.inventory.InsufficientStockException;
import com.ecommerce.monolith.common.exception.inventory.InventoryNotFoundException;
import com.ecommerce.monolith.common.exception.product.ProductExceptionNotFound;
import com.ecommerce.monolith.common.status.InventoryReservationStatus;
import com.ecommerce.monolith.domain.cart.entity.Cart;
import com.ecommerce.monolith.domain.cart.entity.CartItem;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import com.ecommerce.monolith.domain.inventory.entity.Inventory;
import com.ecommerce.monolith.domain.inventory.entity.InventoryReservation;
import com.ecommerce.monolith.domain.inventory.repository.InventoryRepository;
import com.ecommerce.monolith.domain.inventory.repository.InventoryReservationRepository;
import com.ecommerce.monolith.domain.order.dto.CheckoutRequest;
import com.ecommerce.monolith.domain.order.dto.CheckoutSessionResponse;
import com.ecommerce.monolith.domain.order.dto.OrderResponse;
import com.ecommerce.monolith.domain.order.entity.CheckoutSession;
import com.ecommerce.monolith.domain.order.entity.Order;
import com.ecommerce.monolith.common.status.OrderStatus;
import com.ecommerce.monolith.domain.order.mapper.CheckoutMapper;
import com.ecommerce.monolith.domain.order.repository.CheckoutSessionRepository;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.common.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.domain.notification.service.NotificationService;
import com.ecommerce.monolith.common.security.SecurityUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order management service (post-creation operations).
 * <p>Edge Case #8 — State machine: cancellation goes through transitionTo() Edge Case #13 —
 * Read-after-write: listOrders returns @Transactional(readOnly=true) ensuring consistent snapshot
 * reads
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrderService {
    OrderRepository orderRepo;
    NotificationService notificationService;
    CheckoutSessionRepository checkoutSessionRepository;
    CartRepository cartRepository;
    ProductRepository productRepository;
    InventoryRepository inventoryRepository;
    InventoryReservationRepository inventoryReservationRepository;
    CheckoutMapper checkoutMapper;


    public CheckoutSessionResponse checkoutSession(CheckoutRequest request) {
        Optional<Cart> cart = cartRepository.findById(request.cartId());
        List<CartItem> selectedCartItems;
        if (cart.isPresent()) {
            selectedCartItems = cart.get().getItems().stream().filter(CartItem::isSelected).toList();
        } else {
            throw new CartEmptyException("Cart is empty");
        }
        // ? 1. check inventory
        //  Lấy tất cả productId từ cart items
        List<UUID> productIds = selectedCartItems.stream().map(CartItem::getProductId).toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds)
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        // Kiểm tra product nào bị thiếu
        List<UUID> missingProducts = productIds.stream()
                .filter(id -> !productMap.containsKey(id))
                .toList();

        if (!missingProducts.isEmpty()) {
            throw new ProductExceptionNotFound("Not Found Products With IDs: " + missingProducts);
        }

        Map<UUID, Inventory> inventoryMap = inventoryRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Inventory::getProductId, i -> i));
        List<String> outOfStockMessages = selectedCartItems.stream()
                .map(cartItem -> {
                    var productID = cartItem.getProductId();
                    var inventory = inventoryMap.get(productID);

                    if (inventory == null) {
                        throw new InventoryNotFoundException("Not Found Inventory With ID: " + productID);
                    }
                    if (inventory.getQuantity() < cartItem.getQuantity()) {
                        Product product = productMap.get(productID);
                        return String.format("'%s' chỉ còn %d/%d sản phẩm",
                                product.getName(),
                                inventory.getQuantity(),
                                cartItem.getQuantity());
                    }
                    return null;
                }).filter(Objects::nonNull)
                .toList();

        if (!outOfStockMessages.isEmpty()) {
            throw new InsufficientStockException(String.join(", ", outOfStockMessages));
        }
        // 2. Total amount

        BigDecimal total_amount = selectedCartItems.stream()
                .map(item -> item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. generate idempotency key
        UUID idempotencyKey = UUID.randomUUID();

        // 4. inventory reservation
        List<InventoryReservation> inventoryReservations = selectedCartItems.stream()
                .map(cartItem -> {
                    return  InventoryReservation.builder()
                            .productId(cartItem.getProductId())
                            .quantity(cartItem.getQuantity())
                            .status(InventoryReservationStatus.HOLDING)
                            .operation("RESERVE")
                            .createdAt(Instant.now())
                            .build();
                }).toList();

        inventoryReservationRepository.saveAll(inventoryReservations);
        // 5. save session
        CheckoutSession checkoutSession = CheckoutSession.builder()
                .idempotencyKey(idempotencyKey)
                .build();

        checkoutSession = checkoutSessionRepository.save(checkoutSession);
        return checkoutMapper.toCheckoutSessionResponse(checkoutSession);
    }

    /**
     * Edge Case #13 — Read-after-Write Consistency: @Transactional(readOnly=true) tells Hibernate to
     * use the read-replica (if configured) but with snapshot isolation, ensuring consistent reads.
     * The caller who just wrote will see their own data (same connection).
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Order order =
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
        Order order =
                orderRepo
                        .findById(orderId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessRuleViolationException("Order does not belong to you");
        }

        order.transitionTo(OrderStatus.CANCELLED); // Edge Case #8: validated
        orderRepo.save(order);

        // Send cancellation notification directly (Edge Case #11 / Monolith simplified)
        try {
            notificationService.sendOrderCancelled(orderId, userId, "Cancelled by customer");
        } catch (Exception e) {
            log.error("Failed to send order cancellation notification for order={}", orderId, e);
        }

        log.info("Order cancelled: orderId={}", orderId);
    }


    // ─── Helpers ──────────────────────────────────────────────────────────────

    private OrderResponse toResponse(Order o) {
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
