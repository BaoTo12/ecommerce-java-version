package com.ecommerce.monolith.domain.cart.service;

import com.ecommerce.monolith.domain.cart.dto.AddToCartRequest;
import com.ecommerce.monolith.domain.cart.dto.CartResponse;
import com.ecommerce.monolith.domain.cart.entity.Cart;
import com.ecommerce.monolith.domain.cart.entity.CartItem;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import com.ecommerce.monolith.common.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.common.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cart Service
 *
 * <p>Edge Case #7 — Concurrent Cart Modification (Pessimistic Lock): All mutating operations use
 * findActiveByUserIdForUpdate() which holds a database lock for the duration of the transaction.
 *
 * <p>Edge Case #4 — Price Snapshot: When adding to cart, we snapshot the current product price. The
 * checkout step compares this snapshot against the live price.
 */
@Service
@Transactional
public class CartService {

  private final CartRepository cartRepo;
  private final ProductRepository productRepo;

  public CartService(CartRepository cartRepo, ProductRepository productRepo) {
    this.cartRepo = cartRepo;
    this.productRepo = productRepo;
  }

  @Transactional(readOnly = true)
  public CartResponse getCart() {
    UUID userId = SecurityUtils.getCurrentUserId();
    Cart cart = cartRepo.findActiveByUserId(userId).orElse(null);
    if (cart == null) return CartResponse.empty(userId);
    return toResponse(cart);
  }

  /**
   * Edge Case #7: Uses PESSIMISTIC_WRITE lock to prevent concurrent modification. Edge Case #4:
   * Snapshots the current product price into the cart item.
   */
  public CartResponse addItem(AddToCartRequest req) {
    UUID userId = SecurityUtils.getCurrentUserId();

    // Edge Case #7: acquire exclusive lock on the cart row
    Cart cart =
        cartRepo
            .findActiveByUserIdForUpdate(userId)
            .orElseGet(() -> cartRepo.save(Cart.create(userId)));

    // Edge Case #4: capture current price as snapshot
    Product product =
        productRepo
            .findById(req.productId())
            .filter(Product::isActive)
            .orElseThrow(() -> ResourceNotFoundException.of("Product", req.productId()));

    BigDecimal priceSnapshot = product.getPrice(); // ← snapshot at this moment

    // Check if item already in cart
    CartItem existing =
        cart.getItems().stream()
            .filter(i -> i.getProductId().equals(req.productId()))
            .findFirst()
            .orElse(null);

    if (existing != null) {
      existing.updateQuantity(existing.getQuantity() + req.quantity());
    } else {
      CartItem item =
          CartItem.create(
              cart, product.getId(), product.getName(), req.quantity(), priceSnapshot);
      cart.getItems().add(item);
    }
    cart.touch();
    cartRepo.save(cart);
    return toResponse(cart);
  }

  /** Edge Case #7: Pessimistic lock on update. */
  public CartResponse updateItem(UUID itemId, int newQuantity) {
    UUID userId = SecurityUtils.getCurrentUserId();
    Cart cart =
        cartRepo
            .findActiveByUserIdForUpdate(userId)
            .orElseThrow(() -> new BusinessRuleViolationException("No active cart"));

    CartItem item =
        cart.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> ResourceNotFoundException.of("CartItem", itemId));

    if (newQuantity <= 0) {
      cart.getItems().remove(item);
    } else {
      item.updateQuantity(newQuantity);
    }
    cart.touch();
    cartRepo.save(cart);
    return toResponse(cart);
  }

  /** Edge Case #7: Pessimistic lock on remove. */
  public CartResponse removeItem(UUID itemId) {
    return updateItem(itemId, 0);
  }

  public void clearCart(UUID userId) {
    cartRepo
        .findActiveByUserIdForUpdate(userId)
        .ifPresent(
            cart -> {
              cart.getItems().clear();
              cart.touch();
              cartRepo.save(cart);
            });
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private CartResponse toResponse(Cart cart) {
    List<CartResponse.CartItemDto> items =
        cart.getItems().stream()
            .map(
                i ->
                    new CartResponse.CartItemDto(
                        i.getId(),
                        i.getProductId(),
                        i.getProductName(),
                        i.getQuantity(),
                        i.getPriceSnapshot(),
                        i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())),
                        i.isSelected()))
            .toList();

    BigDecimal total =
        items.stream()
            .filter(CartResponse.CartItemDto::selected)
            .map(CartResponse.CartItemDto::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new CartResponse(cart.getId(), cart.getUserId(), items, total, cart.getUpdatedAt());
  }
}
