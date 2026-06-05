package com.ecommerce.monolith.domain.order.service;

import com.ecommerce.monolith.common.exception.AppException;
import com.ecommerce.monolith.common.status.CheckoutSessionStatus;
import com.ecommerce.monolith.common.status.InventoryReservationStatus;
import com.ecommerce.monolith.common.status.OrderStatus;
import com.ecommerce.monolith.domain.cart.entity.Cart;
import com.ecommerce.monolith.domain.cart.entity.CartItem;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.inventory.entity.InventoryReservation;
import com.ecommerce.monolith.domain.inventory.repository.InventoryReservationRepository;
import com.ecommerce.monolith.domain.order.dto.CheckoutExecuteRequest;
import com.ecommerce.monolith.domain.order.entity.CheckoutSession;
import com.ecommerce.monolith.domain.order.entity.Order;
import com.ecommerce.monolith.domain.order.entity.OrderItem;
import com.ecommerce.monolith.domain.order.repository.CheckoutSessionRepository;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.user.entity.Card;
import com.ecommerce.monolith.domain.user.repository.CardRepository;
import com.ecommerce.monolith.domain.user.repository.UserAddressRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutActionService {

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final UserAddressRepository userAddressRepository;
    private final CardRepository cardRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    public record PrepareResult(
            CheckoutSession checkoutSession,
            Order order,
            Card card,
            List<InventoryReservation> reservations) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PrepareResult prepareCheckout(UUID idempotencyKey, CheckoutExecuteRequest request) {
        // ! 1. read and lock Session to prevent others thread read the same checkout session object
        CheckoutSession checkoutSession =
                checkoutSessionRepository
                        .findByIdForUpdate(idempotencyKey)
                        .orElseThrow(
                                () -> new AppException("Checkout session not found", HttpStatus.BAD_REQUEST));

        // ! 2. Check Session Status inside lock
        if (checkoutSession.getStatus() == CheckoutSessionStatus.SUCCESS) {
            return new PrepareResult(checkoutSession, null, null, null);
        }
        if (checkoutSession.getStatus() == CheckoutSessionStatus.PAYMENT_PROCESSING) {
            throw new AppException("Payment is currently running, please wait.", HttpStatus.CONFLICT);
        }

        // ! 3. Load cart items to check reservation expiration
        // load cart
        Cart cart =
                cartRepository
                        .findById(checkoutSession.getCartId())
                        .orElseThrow(() -> new AppException("Cart not found", HttpStatus.BAD_REQUEST));
        // load cart items
        List<CartItem> cartItems = cart.getItems().stream().filter(CartItem::isSelected).toList();
        if (cartItems.isEmpty()) {
            throw new AppException("Your cart is empty.", HttpStatus.BAD_REQUEST);
        }
        // get productIds from cart Item
        List<UUID> productIds = cartItems.stream().map(CartItem::getProductId).toList();

        // Query inventory reservations from product Ids
        List<InventoryReservation> reservations =
                inventoryReservationRepository.findHoldingReservations(
                        productIds,
                        checkoutSession.getCreatedAt().minus(10, ChronoUnit.MINUTES),
                        checkoutSession.getCreatedAt().plus(10, ChronoUnit.MINUTES));

        // Verify the checkout Session is expired?
        Instant now = Instant.now();
        boolean expired =
                checkoutSession.getExpiresAt() != null && now.isAfter(checkoutSession.getExpiresAt());

        if (expired) {
            // Mark session as expired
            checkoutSession.setStatus(CheckoutSessionStatus.EXPIRED);
            checkoutSessionRepository.save(checkoutSession);
            log.info("[DATABASE CHANGE] Marked CheckoutSession={} as EXPIRED", checkoutSession.getIdempotencyKey());

            // Mark reservations as EXPIRED
            for (InventoryReservation res : reservations) {
                res.setStatus(InventoryReservationStatus.EXPIRED);
            }
            inventoryReservationRepository.saveAll(reservations);
            log.info("[DATABASE CHANGE] Marked {} reservations as EXPIRED for productIds={}", reservations.size(), productIds);

            throw new AppException(
                    "Your reservation expired, please refresh your cart.", HttpStatus.BAD_REQUEST);
        }

        // 4. Update status to PAYMENT_PROCESSING
        checkoutSession.setStatus(CheckoutSessionStatus.PAYMENT_PROCESSING);
        checkoutSessionRepository.save(checkoutSession);
        log.info("[DATABASE CHANGE] Updated CheckoutSession={} status to PAYMENT_PROCESSING", checkoutSession.getIdempotencyKey());

        // 5. Card lookup (Fallback to default)
        Card card = null;
        if (request != null && request.cardNumber() != null && !request.cardNumber().trim().isEmpty()) {
            card =
                    Card.builder()
                            .user(null)
                            .cardNumber(request.cardNumber().trim())
                            .cvc(request.cvc())
                            .cardName(request.cardName())
                            .expiry(request.expiry())
                            .isDefault(false)
                            .build();
        } else {
            card =
                    cardRepository
                            .findByUserIdAndIsDefaultTrue(checkoutSession.getUserId())
                            .orElseThrow(
                                    () ->
                                            new AppException(
                                                    "User has no default card. Please register a card first.",
                                                    HttpStatus.BAD_REQUEST));
        }

        // 6. Check for Existing Order - because checkout session can be created or payment_failed
        Order order;
        UUID existingOrderId = checkoutSession.getOrderId();
        if (existingOrderId == null) {
            // Find default address
            var addresses =
                    userAddressRepository
                            .findByUserIdAndIsDefaultTrue(checkoutSession.getUserId())
                            .orElseThrow(
                                    () ->
                                            new AppException(
                                                    "Cannot find Default Address for UserID " + checkoutSession.getUserId(),
                                                    HttpStatus.BAD_REQUEST));

            order =
                    Order.builder()
                            .userId(checkoutSession.getUserId())
                            .totalAmount(checkoutSession.getTotalAmount())
                            .idempotencyKey(idempotencyKey)
                            .shippingAddressId(addresses.getId())
                            .notes("Order placed via execute payment flow")
                            .build();
            order = orderRepository.save(order);
            log.info("[DATABASE CHANGE] Created new Order={} for user={} with totalAmount={}", order.getId(), order.getUserId(), order.getTotalAmount());

            // Create OrderItems
            List<OrderItem> orderItems = new ArrayList<>();
            for (CartItem cartItem : cartItems) {
                var item =
                        OrderItem.builder()
                                .order(order)
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
            order =
                    orderRepository
                            .findById(existingOrderId)
                            .orElseThrow(
                                    () ->
                                            new AppException(
                                                    "Existing order not found: " + existingOrderId, HttpStatus.BAD_REQUEST));
        }

        // Link holding reservations to the order
        for (InventoryReservation res : reservations) {
            res.setOrderId(order.getId());
        }
        inventoryReservationRepository.saveAll(reservations);
        log.info("[DATABASE CHANGE] Linked {} holding reservations to Order={}", reservations.size(), order.getId());

        return new PrepareResult(checkoutSession, order, card, reservations);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeCheckout(
            UUID idempotencyKey,
            UUID orderId,
            boolean paymentSuccess,
            String paymentResultJson,
            String failureReason) {

        CheckoutSession checkoutSession =
                checkoutSessionRepository
                        .findById(idempotencyKey)
                        .orElseThrow(
                                () -> new AppException("Checkout session not found", HttpStatus.BAD_REQUEST));

        Order order =
                orderRepository
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

            // Clear checked-out items from cart
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
