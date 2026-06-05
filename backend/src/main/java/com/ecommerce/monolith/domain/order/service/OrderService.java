package com.ecommerce.monolith.domain.order.service;

import com.ecommerce.monolith.common.exception.AppException;
import com.ecommerce.monolith.common.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.common.exception.ResourceOwnershipException;
import com.ecommerce.monolith.common.security.SecurityUtils;
import com.ecommerce.monolith.common.status.CheckoutSessionStatus;
import com.ecommerce.monolith.common.status.InventoryReservationStatus;
import com.ecommerce.monolith.common.status.OrderStatus;
import com.ecommerce.monolith.domain.cart.entity.Cart;
import com.ecommerce.monolith.domain.cart.entity.CartItem;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import com.ecommerce.monolith.domain.inventory.entity.Inventory;
import com.ecommerce.monolith.domain.inventory.entity.InventoryReservation;
import com.ecommerce.monolith.domain.inventory.repository.InventoryRepository;
import com.ecommerce.monolith.domain.inventory.repository.InventoryReservationRepository;
import com.ecommerce.monolith.domain.notification.service.NotificationService;
import com.ecommerce.monolith.domain.order.dto.CheckoutExecuteRequest;
import com.ecommerce.monolith.domain.order.dto.CheckoutSessionRequest;
import com.ecommerce.monolith.domain.order.dto.CheckoutSessionResponse;
import com.ecommerce.monolith.domain.order.dto.OrderResponse;
import com.ecommerce.monolith.domain.order.entity.CheckoutSession;
import com.ecommerce.monolith.domain.order.entity.Order;
import com.ecommerce.monolith.domain.order.mapper.CheckoutMapper;
import com.ecommerce.monolith.domain.order.mapper.OrderMapper;
import com.ecommerce.monolith.domain.order.repository.CheckoutSessionRepository;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.payment.dto.PaymentDetailsDto;
import com.ecommerce.monolith.domain.payment.dto.PaymentResponse;
import com.ecommerce.monolith.domain.payment.service.PaymentService;
import com.ecommerce.monolith.domain.user.entity.Card;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order management service (post-creation operations).
 *
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
    OrderMapper orderMapper;
    CheckoutActionService checkoutActionService;
    PaymentService paymentService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String checkout(UUID idempotencyKey, CheckoutExecuteRequest request) {

        // Phase 1: Prepare checkout (new transaction)
        CheckoutActionService.PrepareResult result =
                checkoutActionService.prepareCheckout(idempotencyKey, request);

        // If it was already successful in prepareCheckout
        if (result.checkoutSession().getStatus() == CheckoutSessionStatus.SUCCESS) {
            return result.checkoutSession().getResponseBody();
        }

        Order order = result.order();
        Card card = result.card();

        // Phase 2: Call Payment Service/Provider
        PaymentDetailsDto paymentDetails =
                new PaymentDetailsDto(
                        order.getId(),
                        order.getUserId(),
                        order.getTotalAmount(),
                        card.getCardNumber(),
                        card.getCvc(),
                        card.getCardName(),
                        card.getExpiry(),
                        request != null ? request.strategy() : null);

        PaymentResponse paymentResponse = null;
        boolean paymentSuccess = false;
        String failureReason = null;

        try {
            paymentResponse = paymentService.processPayment(paymentDetails);
            if ("CHARGED".equals(paymentResponse.status())) {
                paymentSuccess = true;
            } else {
                failureReason = paymentResponse.failureReason();
            }
        } catch (Exception e) {
            log.error("Payment execution threw exception for order: {}", order.getId(), e);
            failureReason = e.getMessage();
        }

        // Build the success or failure response body
        String responseJson;
        if (paymentSuccess) {
            responseJson =
                    String.format(
                            "{\"status\":\"SUCCESS\",\"orderId\":\"%s\",\"totalAmount\":%s,\"message\":\"Payment completed successfully.\"}",
                            order.getId(), order.getTotalAmount().toString());
        } else {
            responseJson =
                    String.format(
                            "{\"status\":\"FAILED\",\"orderId\":\"%s\",\"totalAmount\":%s,\"message\":\"Payment failed: %s\"}",
                            order.getId(),
                            order.getTotalAmount().toString(),
                            failureReason != null ? failureReason.replace("\"", "\\\"") : "Unknown error");
        }

        // Phase 3: Finalize checkout (new transaction)
        checkoutActionService.finalizeCheckout(
                idempotencyKey, order.getId(), paymentSuccess, responseJson, failureReason);

        if (!paymentSuccess) {
            throw new AppException(
                    failureReason != null ? "Payment failed: " + failureReason : "Payment failed",
                    HttpStatus.BAD_REQUEST);
        }

        return responseJson;
    }

    public CheckoutSessionResponse checkoutSession(CheckoutSessionRequest request) {
        Optional<Cart> cart = cartRepository.findById(request.cartId());
        List<CartItem> selectedCartItems;
        if (cart.isPresent()) {
            selectedCartItems = cart.get().getItems().stream().filter(CartItem::isSelected).toList();
        } else {
            throw new AppException("Your cart is empty.", HttpStatus.BAD_REQUEST);
        }
        // ? 1. check inventory
        //  Lấy tất cả productId từ cart items
        List<UUID> productIds = selectedCartItems.stream().map(CartItem::getProductId).toList();
        Map<UUID, Product> productMap =
                productRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));
        // Kiểm tra product nào bị thiếu
        List<UUID> missingProducts =
                productIds.stream().filter(id -> !productMap.containsKey(id)).toList();

        if (!missingProducts.isEmpty()) {
            throw new AppException(
                    "Not Found Products With IDs: " + missingProducts, HttpStatus.BAD_REQUEST);
        }

        Map<UUID, Inventory> inventoryMap =
                inventoryRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Inventory::getProductId, i -> i));
        List<String> outOfStockMessages =
                selectedCartItems.stream()
                        .map(
                                cartItem -> {
                                    var productID = cartItem.getProductId();
                                    var inventory = inventoryMap.get(productID);

                                    if (inventory == null) {
                                        throw new AppException(
                                                "Not Found Inventory With ID: " + productID, HttpStatus.BAD_REQUEST);
                                    }
                                    if (inventory.getQuantity() < cartItem.getQuantity()) {
                                        Product product = productMap.get(productID);
                                        return String.format(
                                                "'%s' chỉ còn %d/%d sản phẩm",
                                                product.getName(), inventory.getQuantity(), cartItem.getQuantity());
                                    }
                                    return null;
                                })
                        .filter(Objects::nonNull)
                        .toList();

        if (!outOfStockMessages.isEmpty()) {
            throw new AppException(String.join(", ", outOfStockMessages), HttpStatus.BAD_REQUEST);
        }

        // 2. generate idempotency key
        UUID idempotencyKey = UUID.randomUUID();

        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // 4. inventory reservation
        List<InventoryReservation> inventoryReservations =
                selectedCartItems.stream()
                        .map(
                                cartItem -> {
                                    int updated =
                                            inventoryRepository.atomicDecrement(
                                                    cartItem.getProductId(), cartItem.getQuantity());
                                    if (updated != 1) {
                                        throw new AppException(
                                                "Insufficient stock for product: " + cartItem.getProductName(),
                                                HttpStatus.BAD_REQUEST);
                                    }
                                    return InventoryReservation.builder()
                                            .productId(cartItem.getProductId())
                                            .quantity(cartItem.getQuantity())
                                            .status(InventoryReservationStatus.HOLDING)
                                            .operation("RESERVE")
                                            .expiresAt(expiresAt)
                                            .createdAt(Instant.now())
                                            .build();
                                })
                        .toList();

        inventoryReservationRepository.saveAll(inventoryReservations);
        // 5. save session
        CheckoutSession checkoutSession =
                CheckoutSession.builder()
                        .idempotencyKey(idempotencyKey)
                        .userId(cart.get().getUserId())
                        .cartId(cart.get().getId())
                        .totalAmount(request.totalAmount())
                        .expiresAt(expiresAt)
                        .status(CheckoutSessionStatus.CREATED)
                        .build();

        checkoutSession = checkoutSessionRepository.save(checkoutSession);
        return checkoutMapper.toCheckoutSessionResponse(checkoutSession);
    }

    // Edge Case #13 — Read-after-Write Consistency: @Transactional(readOnly=true) tells Hibernate to
    // use the read-replica (if configured) but with snapshot isolation, ensuring consistent reads.
    // The caller who just wrote will see their own data (same connection).
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Order order =
                orderRepo
                        .findById(orderId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceOwnershipException("Order", orderId);
        }
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return orderRepo.findByUserId(userId, pageable).map(orderMapper::toResponse);
    }

    // Edge Case #8 — State Machine: ONLY valid cancellations are allowed. E.g., COMPLETED → CANCELLED
    // is rejected with HTTP 409.
    public void cancelOrder(UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Order order =
                orderRepo
                        .findById(orderId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceOwnershipException("Order", orderId);
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


}
