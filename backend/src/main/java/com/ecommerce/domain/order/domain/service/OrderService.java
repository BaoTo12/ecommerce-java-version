package com.ecommerce.domain.order.domain.service;

import com.ecommerce.common.exception.AppException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ResourceOwnershipException;
import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.common.status.CheckoutSessionStatus;
import com.ecommerce.common.status.InventoryReservationStatus;
import com.ecommerce.common.status.OrderStatus;
import com.ecommerce.domain.cart.domain.model.Cart;
import com.ecommerce.domain.cart.domain.model.CartItem;
import com.ecommerce.domain.cart.domain.ports.out.CartRepositoryPort;
import com.ecommerce.domain.catalog.domain.model.Product;
import com.ecommerce.domain.catalog.domain.ports.out.ProductRepositoryPort;
import com.ecommerce.domain.inventory.domain.model.Inventory;
import com.ecommerce.domain.inventory.domain.model.InventoryReservation;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryRepositoryPort;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryReservationRepositoryPort;
import com.ecommerce.domain.notification.domain.ports.in.NotificationUseCase;
import com.ecommerce.domain.order.domain.model.CheckoutSession;
import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.order.domain.model.OrderItem;
import com.ecommerce.domain.order.domain.ports.in.CheckoutActionUseCase;
import com.ecommerce.domain.order.domain.ports.in.OrderUseCase;
import com.ecommerce.domain.order.domain.ports.out.CheckoutSessionRepositoryPort;
import com.ecommerce.domain.order.domain.ports.out.OrderRepositoryPort;
import com.ecommerce.domain.order.dto.CheckoutExecuteRequest;
import com.ecommerce.domain.order.dto.CheckoutSessionRequest;
import com.ecommerce.domain.order.dto.CheckoutSessionResponse;
import com.ecommerce.domain.order.dto.OrderResponse;
import com.ecommerce.domain.order.mapper.CheckoutMapper;
import com.ecommerce.domain.order.mapper.OrderMapper;
import com.ecommerce.domain.payment.domain.ports.in.PaymentUseCase;
import com.ecommerce.domain.payment.dto.PaymentDetailsDto;
import com.ecommerce.domain.payment.dto.PaymentResponse;
import com.ecommerce.domain.user.domain.model.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class OrderService implements OrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepositoryPort orderRepo;
    private final NotificationUseCase notificationService;
    private final CheckoutSessionRepositoryPort checkoutSessionRepository;
    private final CartRepositoryPort cartRepository;
    private final ProductRepositoryPort productRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final InventoryReservationRepositoryPort inventoryReservationRepository;
    private final CheckoutMapper checkoutMapper;
    private final OrderMapper orderMapper;
    private final CheckoutActionUseCase checkoutActionService;
    private final PaymentUseCase paymentService;

    public OrderService(
            OrderRepositoryPort orderRepo,
            NotificationUseCase notificationService,
            CheckoutSessionRepositoryPort checkoutSessionRepository,
            CartRepositoryPort cartRepository,
            ProductRepositoryPort productRepository,
            InventoryRepositoryPort inventoryRepository,
            InventoryReservationRepositoryPort inventoryReservationRepository,
            CheckoutMapper checkoutMapper,
            OrderMapper orderMapper,
            CheckoutActionUseCase checkoutActionService,
            PaymentUseCase paymentService) {
        this.orderRepo = orderRepo;
        this.notificationService = notificationService;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.checkoutMapper = checkoutMapper;
        this.orderMapper = orderMapper;
        this.checkoutActionService = checkoutActionService;
        this.paymentService = paymentService;
    }

    @Override
    public String checkout(UUID idempotencyKey, CheckoutExecuteRequest request) {
        // Phase 1: Prepare checkout (new transaction internally)
        CheckoutActionService.PrepareResult result = checkoutActionService.prepareCheckout(idempotencyKey, request);

        if (result.checkoutSession().getStatus() == CheckoutSessionStatus.SUCCESS) {
            return result.checkoutSession().getResponseBody();
        }

        Order order = result.order();
        Card card = result.card();

        // Phase 2: Call Payment Service/Provider
        PaymentDetailsDto paymentDetails = new PaymentDetailsDto(
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

        String responseJson;
        if (paymentSuccess) {
            responseJson = String.format(
                    "{\"status\":\"SUCCESS\",\"orderId\":\"%s\",\"totalAmount\":%s,\"message\":\"Payment completed successfully.\"}",
                    order.getId(), order.getTotalAmount().toString());
        } else {
            responseJson = String.format(
                    "{\"status\":\"FAILED\",\"orderId\":\"%s\",\"totalAmount\":%s,\"message\":\"Payment failed: %s\"}",
                    order.getId(),
                    order.getTotalAmount().toString(),
                    failureReason != null ? failureReason.replace("\"", "\\\"") : "Unknown error");
        }

        // Phase 3: Finalize checkout (new transaction internally)
        checkoutActionService.finalizeCheckout(
                idempotencyKey, order.getId(), paymentSuccess, responseJson, failureReason);

        if (!paymentSuccess) {
            throw new AppException(
                    failureReason != null ? "Payment failed: " + failureReason : "Payment failed",
                    HttpStatus.BAD_REQUEST);
        }

        return responseJson;
    }

    @Override
    public CheckoutSessionResponse checkoutSession(CheckoutSessionRequest request) {
        Optional<Cart> cartOpt = cartRepository.findById(request.cartId());
        List<CartItem> selectedCartItems;
        if (cartOpt.isPresent()) {
            selectedCartItems = cartOpt.get().getItems().stream().filter(CartItem::isSelected).toList();
        } else {
            throw new AppException("Your cart is empty.", HttpStatus.BAD_REQUEST);
        }

        List<UUID> productIds = selectedCartItems.stream().map(CartItem::getProductId).toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<UUID> missingProducts = productIds.stream().filter(id -> !productMap.containsKey(id)).toList();
        if (!missingProducts.isEmpty()) {
            throw new AppException("Not Found Products With IDs: " + missingProducts, HttpStatus.BAD_REQUEST);
        }

        Map<UUID, Inventory> inventoryMap = inventoryRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, i -> i));

        List<String> outOfStockMessages = selectedCartItems.stream()
                .map(cartItem -> {
                    var productID = cartItem.getProductId();
                    var inventory = inventoryMap.get(productID);

                    if (inventory == null) {
                        throw new AppException("Not Found Inventory With ID: " + productID, HttpStatus.BAD_REQUEST);
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

        UUID idempotencyKey = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        List<InventoryReservation> inventoryReservations = selectedCartItems.stream()
                .map(cartItem -> {
                    int updated = inventoryRepository.atomicDecrement(cartItem.getProductId(), cartItem.getQuantity());
                    if (updated != 1) {
                        throw new AppException("Insufficient stock for product: " + cartItem.getProductName(), HttpStatus.BAD_REQUEST);
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

        CheckoutSession checkoutSession = CheckoutSession.builder()
                .idempotencyKey(idempotencyKey)
                .userId(cartOpt.get().getUserId())
                .cartId(cartOpt.get().getId())
                .totalAmount(request.totalAmount())
                .expiresAt(expiresAt)
                .status(CheckoutSessionStatus.CREATED)
                .build();

        checkoutSession = checkoutSessionRepository.save(checkoutSession);
        return checkoutMapper.toCheckoutSessionResponse(checkoutSession);
    }

    @Override
    public OrderResponse getOrder(UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Order order = orderRepo
                .findByIdWithItems(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceOwnershipException("Order", orderId);
        }
        return orderMapper.toResponse(order);
    }

    @Override
    public Page<OrderResponse> listOrders(Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return orderRepo.findByUserId(userId, pageable).map(orderMapper::toResponse);
    }

    @Override
    public void cancelOrder(UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Order order = orderRepo
                .findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceOwnershipException("Order", orderId);
        }

        order.transitionTo(OrderStatus.CANCELLED);
        orderRepo.save(order);

        try {
            notificationService.sendOrderCancelled(orderId, userId, "Cancelled by customer");
        } catch (Exception e) {
            log.error("Failed to send order cancellation notification for order={}", orderId, e);
        }

        log.info("Order cancelled: orderId={}", orderId);
    }
}
