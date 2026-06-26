package com.ecommerce.domain.order.domain.service;

import com.ecommerce.common.exception.AppException;
import com.ecommerce.common.status.CheckoutSessionStatus;
import com.ecommerce.common.status.InventoryReservationStatus;
import com.ecommerce.common.status.OrderStatus;
import com.ecommerce.domain.cart.domain.model.Cart;
import com.ecommerce.domain.cart.domain.model.CartItem;
import com.ecommerce.domain.cart.domain.ports.out.CartRepositoryPort;
import com.ecommerce.domain.inventory.domain.model.InventoryReservation;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryReservationRepositoryPort;
import com.ecommerce.domain.order.domain.model.CheckoutSession;
import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.order.domain.model.OrderItem;
import com.ecommerce.domain.order.domain.ports.in.CheckoutActionUseCase;
import com.ecommerce.domain.order.domain.ports.out.CheckoutSessionRepositoryPort;
import com.ecommerce.domain.order.domain.ports.out.OrderRepositoryPort;
import com.ecommerce.domain.order.dto.CheckoutExecuteRequest;
import com.ecommerce.domain.user.domain.model.Card;
import com.ecommerce.domain.user.domain.ports.out.CardRepositoryPort;
import com.ecommerce.domain.user.domain.ports.out.UserAddressRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CheckoutActionService implements CheckoutActionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckoutActionService.class);

    private final CheckoutSessionRepositoryPort checkoutSessionRepository;
    private final CartRepositoryPort cartRepository;
    private final OrderRepositoryPort orderRepository;
    private final UserAddressRepositoryPort userAddressRepository;
    private final CardRepositoryPort cardRepository;
    private final InventoryReservationRepositoryPort inventoryReservationRepository;

    public CheckoutActionService(
            CheckoutSessionRepositoryPort checkoutSessionRepository,
            CartRepositoryPort cartRepository,
            OrderRepositoryPort orderRepository,
            UserAddressRepositoryPort userAddressRepository,
            CardRepositoryPort cardRepository,
            InventoryReservationRepositoryPort inventoryReservationRepository) {
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.userAddressRepository = userAddressRepository;
        this.cardRepository = cardRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
    }

    public record PrepareResult(
            CheckoutSession checkoutSession,
            Order order,
            Card card,
            List<InventoryReservation> reservations) {
    }

    @Override
    public PrepareResult prepareCheckout(UUID idempotencyKey, CheckoutExecuteRequest request) {
        CheckoutSession checkoutSession = checkoutSessionRepository
                .findByIdForUpdate(idempotencyKey)
                .orElseThrow(() -> new AppException("Checkout session not found", HttpStatus.BAD_REQUEST));

        if (checkoutSession.getStatus() == CheckoutSessionStatus.SUCCESS) {
            return new PrepareResult(checkoutSession, null, null, null);
        }
        if (checkoutSession.getStatus() == CheckoutSessionStatus.PAYMENT_PROCESSING) {
            throw new AppException("Payment is currently running, please wait.", HttpStatus.CONFLICT);
        }

        Cart cart = cartRepository
                .findById(checkoutSession.getCartId())
                .orElseThrow(() -> new AppException("Cart not found", HttpStatus.BAD_REQUEST));

        List<CartItem> cartItems = cart.getItems().stream().filter(CartItem::isSelected).toList();
        if (cartItems.isEmpty()) {
            throw new AppException("Your cart is empty.", HttpStatus.BAD_REQUEST);
        }
        List<UUID> productIds = cartItems.stream().map(CartItem::getProductId).toList();

        List<InventoryReservation> reservations = inventoryReservationRepository.findHoldingReservations(
                productIds,
                checkoutSession.getCreatedAt().minus(10, ChronoUnit.MINUTES),
                checkoutSession.getCreatedAt().plus(10, ChronoUnit.MINUTES));

        Instant now = Instant.now();
        boolean expired = checkoutSession.getExpiresAt() != null && now.isAfter(checkoutSession.getExpiresAt());

        if (expired) {
            checkoutSession.setStatus(CheckoutSessionStatus.EXPIRED);
            checkoutSessionRepository.save(checkoutSession);
            log.info("[DATABASE CHANGE] Marked CheckoutSession={} as EXPIRED", checkoutSession.getIdempotencyKey());

            for (InventoryReservation res : reservations) {
                res.setStatus(InventoryReservationStatus.EXPIRED);
            }
            inventoryReservationRepository.saveAll(reservations);
            log.info("[DATABASE CHANGE] Marked {} reservations as EXPIRED for productIds={}", reservations.size(), productIds);

            throw new AppException(
                    "Your reservation expired, please refresh your cart.", HttpStatus.BAD_REQUEST);
        }

        checkoutSession.setStatus(CheckoutSessionStatus.PAYMENT_PROCESSING);
        checkoutSessionRepository.save(checkoutSession);
        log.info("[DATABASE CHANGE] Updated CheckoutSession={} status to PAYMENT_PROCESSING", checkoutSession.getIdempotencyKey());

        Card card = null;
        if (request != null && request.cardNumber() != null && !request.cardNumber().trim().isEmpty()) {
            card = Card.builder()
                    .cardNumber(request.cardNumber().trim())
                    .cvc(request.cvc())
                    .cardName(request.cardName())
                    .expiry(request.expiry())
                    .isDefault(false)
                    .build();
        } else {
            card = cardRepository
                    .findByUserIdAndIsDefaultTrue(checkoutSession.getUserId())
                    .orElseThrow(() -> new AppException("User has no default card. Please register a card first.", HttpStatus.BAD_REQUEST));
        }

        Order order;
        UUID existingOrderId = checkoutSession.getOrderId();
        if (existingOrderId == null) {
            var address = userAddressRepository
                    .findByUserIdAndIsDefaultTrue(checkoutSession.getUserId())
                    .orElseThrow(() -> new AppException("Cannot find Default Address for UserID " + checkoutSession.getUserId(), HttpStatus.BAD_REQUEST));

            order = Order.builder()
                    .userId(checkoutSession.getUserId())
                    .totalAmount(checkoutSession.getTotalAmount())
                    .idempotencyKey(idempotencyKey)
                    .shippingAddressId(address.getId())
                    .notes("Order placed via execute payment flow")
                    .build();
            order = orderRepository.save(order);
            log.info("[DATABASE CHANGE] Created new Order={} for user={} with totalAmount={}", order.getId(), order.getUserId(), order.getTotalAmount());

            List<OrderItem> orderItems = new ArrayList<>();
            for (CartItem cartItem : cartItems) {
                var item = OrderItem.builder()
                        .productId(cartItem.getProductId())
                        .productName(cartItem.getProductName())
                        .quantity(cartItem.getQuantity())
                        .build();
                orderItems.add(item);
            }
            order.getItems().addAll(orderItems);
            orderRepository.save(order);
            log.info("[DATABASE CHANGE] Saved {} OrderItems for Order={}", order.getItems().size(), order.getId());

            checkoutSession.setOrderId(order.getId());
            checkoutSessionRepository.save(checkoutSession);
            log.info("[DATABASE CHANGE] Associated Order={} with CheckoutSession={}", order.getId(), checkoutSession.getIdempotencyKey());
        } else {
            order = orderRepository
                    .findById(existingOrderId)
                    .orElseThrow(() -> new AppException("Existing order not found: " + existingOrderId, HttpStatus.BAD_REQUEST));
        }

        for (InventoryReservation res : reservations) {
            res.setOrderId(order.getId());
        }
        inventoryReservationRepository.saveAll(reservations);
        log.info("[DATABASE CHANGE] Linked {} holding reservations to Order={}", reservations.size(), order.getId());

        return new PrepareResult(checkoutSession, order, card, reservations);
    }

    @Override
    public void finalizeCheckout(
            UUID idempotencyKey,
            UUID orderId,
            boolean paymentSuccess,
            String paymentResultJson,
            String failureReason) {

        CheckoutSession checkoutSession = checkoutSessionRepository
                .findById(idempotencyKey)
                .orElseThrow(() -> new AppException("Checkout session not found", HttpStatus.BAD_REQUEST));

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.BAD_REQUEST));

        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(orderId);

        if (paymentSuccess) {
            if (order.getStatus() != OrderStatus.PAID) {
                order.transitionTo(OrderStatus.PAID);
                orderRepository.save(order);
                log.info("[DATABASE CHANGE] Transitioned Order={} status to PAID", order.getId());
            }

            checkoutSession.setStatus(CheckoutSessionStatus.SUCCESS);
            checkoutSession.setResponseBody(paymentResultJson);
            checkoutSessionRepository.save(checkoutSession);
            log.info("[DATABASE CHANGE] Updated CheckoutSession={} status to SUCCESS", checkoutSession.getIdempotencyKey());

            for (InventoryReservation res : reservations) {
                res.setStatus(InventoryReservationStatus.COMPLETED);
            }
            inventoryReservationRepository.saveAll(reservations);
            log.info("[DATABASE CHANGE] Completed {} reservations for Order={}", reservations.size(), orderId);

            cartRepository.findById(checkoutSession.getCartId()).ifPresent(cart -> {
                List<UUID> checkedOutProductIds = order.getItems().stream()
                        .map(OrderItem::getProductId)
                        .toList();
                int initialSize = cart.getItems().size();
                cart.getItems().removeIf(item -> checkedOutProductIds.contains(item.getProductId()));
                cart.touch();
                cartRepository.save(cart);
                log.info("[DATABASE CHANGE] Cleared {} checked-out items from Cart={} (remaining: {})", initialSize - cart.getItems().size(), checkoutSession.getCartId(), cart.getItems().size());
            });
        } else {
            order.transitionTo(OrderStatus.PENDING);
            orderRepository.save(order);
            log.info("[DATABASE CHANGE] Transitioned Order={} status to PENDING (payment failed)", order.getId());

            checkoutSession.setStatus(CheckoutSessionStatus.PAYMENT_FAILED);
            checkoutSessionRepository.save(checkoutSession);
            log.info("[DATABASE CHANGE] Updated CheckoutSession={} status to PAYMENT_FAILED", checkoutSession.getIdempotencyKey());
        }
    }
}
