package com.ecommerce.domain.cart.domain.service;

import com.ecommerce.common.exception.BusinessRuleViolationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.domain.cart.domain.model.Cart;
import com.ecommerce.domain.cart.domain.model.CartItem;
import com.ecommerce.domain.cart.domain.ports.in.CartUseCase;
import com.ecommerce.domain.cart.domain.ports.out.CartRepositoryPort;
import com.ecommerce.domain.cart.dto.AddToCartRequest;
import com.ecommerce.domain.cart.dto.CartResponse;
import com.ecommerce.domain.cart.mapper.CartMapper;
import com.ecommerce.domain.catalog.domain.model.Product;
import com.ecommerce.domain.catalog.domain.ports.out.ProductRepositoryPort;

import java.math.BigDecimal;
import java.util.UUID;

public class CartService implements CartUseCase {

    private final CartRepositoryPort cartRepo;
    private final ProductRepositoryPort productRepo;
    private final CartMapper cartMapper;

    public CartService(CartRepositoryPort cartRepo, ProductRepositoryPort productRepo, CartMapper cartMapper) {
        this.cartRepo = cartRepo;
        this.productRepo = productRepo;
        this.cartMapper = cartMapper;
    }

    @Override
    public CartResponse getCart() {
        UUID userId = SecurityUtils.getCurrentUserId();
        Cart cart = cartRepo.findActiveByUserId(userId).orElse(null);
        if (cart == null) return CartResponse.empty(userId);
        return cartMapper.toResponse(cart);
    }

    @Override
    public CartResponse addItem(AddToCartRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();

        Cart cart = cartRepo
                .findActiveByUserIdForUpdate(userId)
                .orElseGet(() -> cartRepo.save(Cart.builder().userId(userId).build()));

        Product product = productRepo
                .findById(req.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", req.productId()));

        BigDecimal priceSnapshot = product.getPrice();

        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(req.productId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.updateQuantity(existing.getQuantity() + req.quantity());
        } else {
            CartItem item = CartItem.builder()
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

    @Override
    public CartResponse updateItem(UUID itemId, int newQuantity) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Cart cart = cartRepo
                .findActiveByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("No active cart"));

        CartItem item = cart.getItems().stream()
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

    @Override
    public CartResponse removeItem(UUID itemId) {
        return updateItem(itemId, 0);
    }
}
