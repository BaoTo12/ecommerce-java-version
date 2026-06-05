package com.ecommerce.monolith.domain.cart.service;

import com.ecommerce.monolith.common.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.common.security.SecurityUtils;
import com.ecommerce.monolith.domain.cart.dto.AddToCartRequest;
import com.ecommerce.monolith.domain.cart.dto.CartResponse;
import com.ecommerce.monolith.domain.cart.entity.Cart;
import com.ecommerce.monolith.domain.cart.entity.CartItem;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.cart.mapper.CartMapper;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {

  private final CartRepository cartRepo;
  private final ProductRepository productRepo;
  private final CartMapper cartMapper;

  public CartService(CartRepository cartRepo, ProductRepository productRepo, CartMapper cartMapper) {
    this.cartRepo = cartRepo;
    this.productRepo = productRepo;
    this.cartMapper = cartMapper;
  }

  @Transactional(readOnly = true)
  public CartResponse getCart() {
    UUID userId = SecurityUtils.getCurrentUserId();
    Cart cart = cartRepo.findActiveByUserId(userId).orElse(null);
    if (cart == null) return CartResponse.empty(userId);
    return cartMapper.toResponse(cart);
  }

  // Edge Case #7: Uses PESSIMISTIC_WRITE lock to prevent concurrent modification.
  // Edge Case #4: Snapshots the current product price into the cart item.
  public CartResponse addItem(AddToCartRequest req) {
    UUID userId = SecurityUtils.getCurrentUserId();

    // Edge Case #7: acquire exclusive lock on the cart row
    Cart cart =
        cartRepo
            .findActiveByUserIdForUpdate(userId)
            .orElseGet(() -> cartRepo.save(Cart.builder().userId(userId).build()));

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
          CartItem.builder()
              .cart(cart)
              .productId(product.getId())
              .productName(product.getName())
              .quantity(req.quantity())
              .priceSnapshot(priceSnapshot)
              .build();
      cart.getItems().add(item);
    }
    cart.touch();
    cartRepo.save(cart);
    return cartMapper.toResponse(cart);
  }

  // Edge Case #7: Pessimistic lock on update
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
    return cartMapper.toResponse(cart);
  }

  // Edge Case #7: Pessimistic lock on remove
  public CartResponse removeItem(UUID itemId) {
    return updateItem(itemId, 0);
  }
}
