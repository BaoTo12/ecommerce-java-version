package com.ecommerce.order.service;

import com.ecommerce.order.model.dto.CheckoutRequest;
import com.ecommerce.order.model.dto.CheckoutResponse;
import com.ecommerce.order.model.dto.CreateOrderRequest;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.model.entity.CartEntity;
import com.ecommerce.order.model.entity.CheckoutSessionEntity;
import com.ecommerce.order.repository.CartRepository;
import com.ecommerce.order.repository.CheckoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartRepository cartRepo;
    private final CheckoutSessionRepository checkoutRepo;
    private final OrderService orderService;

    public CheckoutService(CartRepository cartRepo, CheckoutSessionRepository checkoutRepo,
                           OrderService orderService) {
        this.cartRepo = cartRepo;
        this.checkoutRepo = checkoutRepo;
        this.orderService = orderService;
    }

    public CheckoutResponse checkout(CheckoutRequest req) {
        CartEntity cart = cartRepo.findActiveByUserId(req.userId())
                .orElseThrow(() -> new RuntimeException("No active cart for user: " + req.userId()));

        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        // Calculate subtotal
        BigDecimal subtotal = cart.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create checkout session
        CheckoutSessionEntity session = CheckoutSessionEntity.create(cart.getId(), req.userId(), subtotal);
        session.processing();
        checkoutRepo.save(session);

        try {
            // Build order from cart
            List<CreateOrderRequest.OrderItemRequest> orderItems = cart.getItems().stream()
                    .map(i -> new CreateOrderRequest.OrderItemRequest(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                    .toList();

            CreateOrderRequest orderReq = new CreateOrderRequest(req.userId(), orderItems);
            OrderResponse orderResp = orderService.createOrder(orderReq);

            // Mark cart as checked out and session as completed
            cart.checkOut();
            cartRepo.save(cart);
            session.complete(orderResp.id());
            checkoutRepo.save(session);

            log.info("Checkout completed: sessionId={}, orderId={}", session.getId(), orderResp.id());
            return new CheckoutResponse(session.getId(), orderResp.id(), "COMPLETED", orderResp.totalAmount());

        } catch (Exception e) {
            session.fail(e.getMessage());
            checkoutRepo.save(session);
            log.error("Checkout failed: sessionId={}", session.getId(), e);
            throw e;
        }
    }
}
