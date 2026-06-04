package com.ecommerce.monolith.domain.order.service;

import com.ecommerce.monolith.domain.cart.entity.CartEntity;
import com.ecommerce.monolith.domain.cart.entity.CartItemEntity;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.catalog.entity.ProductEntity;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import com.ecommerce.monolith.domain.inventory.service.InventoryService;
import com.ecommerce.monolith.domain.order.dto.CheckoutRequest;
import com.ecommerce.monolith.domain.order.dto.CheckoutResponse;
import com.ecommerce.monolith.domain.order.entity.OrderEntity;
import com.ecommerce.monolith.domain.order.entity.OrderItemEntity;
import com.ecommerce.monolith.domain.order.entity.OrderStatusHistoryEntity;
import com.ecommerce.monolith.domain.order.enums.OrderStatus;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.order.repository.OrderStatusHistoryRepository;
import com.ecommerce.monolith.domain.payment.service.PaymentService;
import com.ecommerce.monolith.domain.user.service.UserService;
import com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.infrastructure.exception.PriceChangedException;
import com.ecommerce.monolith.infrastructure.exception.ResourceNotFoundException;
import com.ecommerce.monolith.domain.notification.service.NotificationService;
import com.ecommerce.monolith.infrastructure.security.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout Service — converts a cart into an order in a single atomic transaction.
 *
 * <p>Edge Case #4 — Price Snapshot Validation: checks live price vs cart snapshot Edge Case #16 —
 * Address Ownership: verifies shipping address belongs to user Edge Case #17 — Checkout Atomicity:
 * cart + order in ONE transaction Edge Case #1 — Idempotency: checks for duplicate checkout via
 * idempotency key
 */
@Service
public class CheckoutService {

  private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

  private final CartRepository cartRepo;
  private final ProductRepository productRepo;
  private final OrderRepository orderRepo;
  private final OrderStatusHistoryRepository historyRepo;
  private final InventoryService inventoryService;
  private final PaymentService paymentService;
  private final UserService userService;
  private final NotificationService notificationService;
  private final com.ecommerce.monolith.domain.coupon.service.CouponService couponService;

  @Value("${app.price.snapshot-tolerance-percent:1.0}")
  private double priceTolerancePercent;

  public CheckoutService(
      CartRepository cartRepo,
      ProductRepository productRepo,
      OrderRepository orderRepo,
      OrderStatusHistoryRepository historyRepo,
      InventoryService inventoryService,
      PaymentService paymentService,
      UserService userService,
      NotificationService notificationService,
      com.ecommerce.monolith.domain.coupon.service.CouponService couponService) {
    this.cartRepo = cartRepo;
    this.productRepo = productRepo;
    this.orderRepo = orderRepo;
    this.historyRepo = historyRepo;
    this.inventoryService = inventoryService;
    this.paymentService = paymentService;
    this.userService = userService;
    this.notificationService = notificationService;
    this.couponService = couponService;
  }

  /**
   * Edge Case #17 — Checkout Atomicity: Everything here — reading the cart, validating prices,
   * reserving inventory, creating the order, and clearing the cart — happens in ONE @Transactional.
   *
   * <p>If any step throws, the whole thing rolls back: - No order is created - Cart is NOT cleared
   * (user can retry) - No inventory is reserved
   *
   * <p>This prevents the dreaded "cart cleared but no order" bug.
   */
  @Transactional
  public CheckoutResponse checkout(CheckoutRequest req) {
    UUID userId = SecurityUtils.getCurrentUserId();

    // Edge Case #1: Idempotency check
    if (req.idempotencyKey() != null) {
      return orderRepo
          .findByIdempotencyKey(req.idempotencyKey())
          .map(
              existing -> {
                log.info("Returning existing order for idempotency key={}", req.idempotencyKey());
                return new CheckoutResponse(
                    existing.getId(),
                    existing.getStatus().name(),
                    existing.getTotalAmount(),
                    "DUPLICATE_REQUEST");
              })
          .orElseGet(() -> performCheckout(userId, req));
    }

    return performCheckout(userId, req);
  }

  private CheckoutResponse performCheckout(UUID userId, CheckoutRequest req) {
    // Edge Case #16: Verify shipping address ownership
    if (req.shippingAddressId() != null) {
      userService.loadAddressAndVerifyOwnership(req.shippingAddressId());
    }

    // Edge Case #7: Lock the cart to prevent concurrent modifications during checkout
    CartEntity cart =
        cartRepo
            .findActiveByUserIdForUpdate(userId)
            .orElseThrow(() -> new BusinessRuleViolationException("No active cart to checkout"));

    if (cart.getItems().isEmpty()) {
      throw new BusinessRuleViolationException("Cart is empty");
    }

    List<CartItemEntity> selectedItems =
        cart.getItems().stream().filter(CartItemEntity::isSelected).toList();

    if (selectedItems.isEmpty()) {
      throw new BusinessRuleViolationException("No items selected for checkout");
    }

    // ─── Edge Case #4: Price Snapshot Validation ────────────────────────────
    // Load current prices and compare against what the user saw when adding to cart.
    // If any price changed beyond the tolerance, reject the checkout.
    List<Map.Entry<CartItemEntity, ProductEntity>> enriched = new ArrayList<>();
    for (CartItemEntity item : selectedItems) {
      ProductEntity product =
          productRepo
              .findById(item.getProductId())
              .filter(ProductEntity::isActive)
              .orElseThrow(() -> ResourceNotFoundException.of("Product", item.getProductId()));

      BigDecimal livePrice = product.getPrice();
      BigDecimal snapshot = item.getPriceSnapshot();
      double diff =
          livePrice
              .subtract(snapshot)
              .abs()
              .divide(snapshot, 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .doubleValue();

      if (diff > priceTolerancePercent) {
        log.warn(
            "Price changed for product={}: snapshot={}, live={}, diff={}%",
            product.getSku(), snapshot, livePrice, diff);
        throw new PriceChangedException(product.getSku(), snapshot, livePrice);
      }

      enriched.add(Map.entry(item, product));
    }
    // ────────────────────────────────────────────────────────────────────────

    // Calculate total using LIVE prices (not snapshots) after validation passes
    BigDecimal total =
        enriched.stream()
            .map(
                e -> e.getValue().getPrice().multiply(BigDecimal.valueOf(e.getKey().getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // ─── Edge Case #17: Create Order atomically ──────────────────────────────
    OrderEntity order =
        OrderEntity.create(userId, req.shippingAddressId(), req.notes(), req.idempotencyKey());
    order.setTotalAmount(total);

    for (Map.Entry<CartItemEntity, ProductEntity> entry : enriched) {
      CartItemEntity cartItem = entry.getKey();
      ProductEntity product = entry.getValue();
      OrderItemEntity orderItem =
          OrderItemEntity.create(
              order,
              product.getId(),
              product.getName(),
              cartItem.getQuantity(),
              product.getPrice());
      order.getItems().add(orderItem);
    }

    order.transitionTo(OrderStatus.CONFIRMED); // Edge Case #8: state machine
    order = orderRepo.saveAndFlush(order);

    // Apply coupon if present (Edge Case #21)
    if (req.couponCode() != null && !req.couponCode().isBlank()) {
      var couponResult =
          couponService.applyCoupon(
              new com.ecommerce.monolith.domain.coupon.dto.ApplyCouponRequest(
                  req.couponCode(), total),
              userId,
              order.getId());
      total = couponResult.finalAmount();
      order.setTotalAmount(total);
      order = orderRepo.save(order);
    }

    historyRepo.save(
        OrderStatusHistoryEntity.of(
            order.getId(),
            OrderStatus.PENDING.name(),
            OrderStatus.CONFIRMED.name(),
            "Order created via checkout"));

    // Mark cart as checked out WITHIN THE SAME TRANSACTION
    // If order creation fails, cart remains ACTIVE → user can retry
    cart.markCheckedOut();
    cartRepo.save(cart);
    // ────────────────────────────────────────────────────────────────────────

    // Reserve inventory (synchronous in monolith)
    try {
      inventoryService.reserveForOrder(order);
    } catch (Exception e) {
      // Inventory reservation failed — transition order to appropriate status
      order.transitionTo(OrderStatus.PAYMENT_FAILED); // fallback
      orderRepo.save(order);
      throw new BusinessRuleViolationException("Inventory reservation failed: " + e.getMessage());
    }

    // Send order confirmation notification directly (Edge Case #11 / Monolith simplified)
    try {
      notificationService.sendOrderConfirmed(order.getId(), userId);
    } catch (Exception e) {
      log.error("Failed to send order confirmation notification for order={}", order.getId(), e);
    }

    log.info("Checkout successful: orderId={}, userId={}, total={}", order.getId(), userId, total);
    return new CheckoutResponse(order.getId(), order.getStatus().name(), total, null);
  }
}
