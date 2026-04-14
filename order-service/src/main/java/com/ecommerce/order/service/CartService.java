package com.ecommerce.order.service;

import com.ecommerce.order.model.dto.AddCartItemRequest;
import com.ecommerce.order.model.dto.CartResponse;
import com.ecommerce.order.model.dto.CartResponse.CartItemDto;
import com.ecommerce.order.model.entity.CartEntity;
import com.ecommerce.order.model.entity.CartItemEntity;
import com.ecommerce.order.model.entity.ProductCatalogEntity;
import com.ecommerce.order.repository.CartItemRepository;
import com.ecommerce.order.repository.CartRepository;
import com.ecommerce.order.repository.ProductCatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductCatalogRepository productRepo;

    public CartService(CartRepository cartRepo, CartItemRepository cartItemRepo,
                       ProductCatalogRepository productRepo) {
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.productRepo = productRepo;
    }

    public CartResponse addItem(AddCartItemRequest req) {
        ProductCatalogEntity product = productRepo.findById(req.productId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + req.productId()));

        CartEntity cart = cartRepo.findActiveByUserId(req.userId())
                .orElseGet(() -> cartRepo.save(CartEntity.create(req.userId())));

        // Check if product already in cart
        var existingItem = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(req.productId()))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().updateQuantity(existingItem.get().getQuantity() + req.quantity());
        } else {
            CartItemEntity item = new CartItemEntity(cart, req.productId(), req.quantity(), product.getPrice());
            cart.getItems().add(item);
        }
        cartRepo.save(cart);
        return toResponse(cart);
    }

    public CartResponse updateItem(UUID itemId, int newQuantity) {
        CartItemEntity item = cartItemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found: " + itemId));
        item.updateQuantity(newQuantity);
        cartItemRepo.save(item);
        CartEntity cart = item.getCart();
        return toResponse(cart);
    }

    public void removeItem(UUID itemId) {
        cartItemRepo.deleteById(itemId);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        CartEntity cart = cartRepo.findActiveByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No active cart for user: " + userId));
        return toResponse(cart);
    }

    private CartResponse toResponse(CartEntity cart) {
        List<CartItemDto> itemDtos = cart.getItems().stream().map(i -> {
            String productName = productRepo.findById(i.getProductId())
                    .map(ProductCatalogEntity::getName).orElse("Unknown");
            BigDecimal lineTotal = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
            return new CartItemDto(i.getId(), i.getProductId(), productName, i.getQuantity(), i.getUnitPrice(), lineTotal);
        }).toList();

        BigDecimal subtotal = itemDtos.stream()
                .map(CartItemDto::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), cart.getUserId(), cart.getStatus(), itemDtos, subtotal);
    }
}
