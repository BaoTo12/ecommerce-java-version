package com.ecommerce.common.config;

import com.ecommerce.common.security.JwtUtil;
import com.ecommerce.domain.cart.domain.ports.in.CartUseCase;
import com.ecommerce.domain.cart.domain.ports.out.CartRepositoryPort;
import com.ecommerce.domain.cart.domain.service.CartService;
import com.ecommerce.domain.cart.mapper.CartMapper;
import com.ecommerce.domain.catalog.domain.ports.in.CatalogUseCase;
import com.ecommerce.domain.catalog.domain.ports.out.ProductRepositoryPort;
import com.ecommerce.domain.catalog.domain.service.CatalogService;
import com.ecommerce.domain.inventory.domain.ports.in.BenchmarkUseCase;
import com.ecommerce.domain.inventory.domain.ports.in.InventoryUseCase;
import com.ecommerce.domain.inventory.domain.ports.in.ReservationCleanupUseCase;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryRepositoryPort;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryReservationRepositoryPort;
import com.ecommerce.domain.inventory.domain.service.BenchmarkService;
import com.ecommerce.domain.inventory.domain.service.InventoryService;
import com.ecommerce.domain.inventory.domain.service.ReservationCleanupJob;
import com.ecommerce.domain.notification.domain.ports.in.NotificationUseCase;
import com.ecommerce.domain.notification.domain.ports.out.EmailSenderPort;
import com.ecommerce.domain.notification.domain.ports.out.NotificationRepositoryPort;
import com.ecommerce.domain.notification.domain.ports.out.NotificationUserRepositoryPort;
import com.ecommerce.domain.notification.domain.service.NotificationService;
import com.ecommerce.domain.order.domain.ports.in.CheckoutActionUseCase;
import com.ecommerce.domain.order.domain.ports.in.OrderUseCase;
import com.ecommerce.domain.order.domain.ports.out.CheckoutSessionRepositoryPort;
import com.ecommerce.domain.order.domain.ports.out.OrderRepositoryPort;
import com.ecommerce.domain.order.domain.service.CheckoutActionService;
import com.ecommerce.domain.order.domain.service.OrderService;
import com.ecommerce.domain.order.mapper.CheckoutMapper;
import com.ecommerce.domain.order.mapper.OrderMapper;
import com.ecommerce.domain.payment.domain.ports.in.PaymentUseCase;
import com.ecommerce.domain.payment.domain.ports.out.PaymentGatewayPort;
import com.ecommerce.domain.payment.domain.ports.out.PaymentRepositoryPort;
import com.ecommerce.domain.payment.domain.service.PaymentService;
import com.ecommerce.domain.payment.mapper.PaymentMapper;
import com.ecommerce.domain.user.domain.ports.in.AuthUseCase;
import com.ecommerce.domain.user.domain.ports.in.UserUseCase;
import com.ecommerce.domain.user.domain.ports.out.CardRepositoryPort;
import com.ecommerce.domain.user.domain.ports.out.RefreshTokenRepositoryPort;
import com.ecommerce.domain.user.domain.ports.out.UserAddressRepositoryPort;
import com.ecommerce.domain.user.domain.ports.out.UserRepositoryPort;
import com.ecommerce.domain.user.domain.service.AuthService;
import com.ecommerce.domain.user.domain.service.UserService;
import com.ecommerce.domain.user.mapper.UserMapper;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DomainConfig implements SmartInitializingSingleton {

    private final ListableBeanFactory beanFactory;

    @Value("${app.retry.max-attempts:3}")
    private int maxRetry;

    @Value("${app.retry.backoff-ms:50}")
    private long backoffMs;

    @Value("${app.inventory.reservation-ttl-minutes:30}")
    private long reservationTtlMinutes;

    public DomainConfig(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        InventoryService inventoryService = (InventoryService) beanFactory.getBean("inventoryService");
        inventoryService.setSelf(inventoryService);

        BenchmarkService benchmarkService = (BenchmarkService) beanFactory.getBean("benchmarkService");
        benchmarkService.setSelf(benchmarkService);
    }

    @Bean
    public CartUseCase cartService(
            CartRepositoryPort cartRepo,
            ProductRepositoryPort productRepo,
            CartMapper cartMapper) {
        return new CartService(cartRepo, productRepo, cartMapper);
    }

    @Bean
    public CatalogUseCase catalogService(ProductRepositoryPort productRepo) {
        return new CatalogService(productRepo);
    }

    @Bean
    public InventoryUseCase inventoryService(
            InventoryRepositoryPort inventoryRepo,
            InventoryReservationRepositoryPort reservationRepo) {
        return new InventoryService(inventoryRepo, reservationRepo, maxRetry, backoffMs, reservationTtlMinutes);
    }

    @Bean
    public BenchmarkUseCase benchmarkService(InventoryRepositoryPort inventoryRepo) {
        return new BenchmarkService(inventoryRepo);
    }

    @Bean
    public ReservationCleanupUseCase reservationCleanupJob(
            InventoryReservationRepositoryPort inventoryReservationRepository,
            InventoryRepositoryPort inventoryRepository,
            CheckoutSessionRepositoryPort checkoutSessionRepository) {
        return new ReservationCleanupJob(
                inventoryReservationRepository,
                inventoryRepository,
                checkoutSessionRepository);
    }

    @Bean
    public NotificationUseCase notificationService(
            NotificationRepositoryPort notificationRepo,
            NotificationUserRepositoryPort userRepo,
            EmailSenderPort emailSender) {
        return new NotificationService(notificationRepo, userRepo, emailSender);
    }

    @Bean
    public PaymentUseCase paymentService(
            PaymentRepositoryPort paymentRepo,
            OrderRepositoryPort orderRepo,
            PaymentGatewayPort gateway,
            NotificationUseCase notificationService,
            PaymentMapper paymentMapper) {
        return new PaymentService(paymentRepo, orderRepo, gateway, notificationService, paymentMapper);
    }

    @Bean
    public CheckoutActionUseCase checkoutActionService(
            CheckoutSessionRepositoryPort checkoutSessionRepository,
            CartRepositoryPort cartRepository,
            OrderRepositoryPort orderRepository,
            UserAddressRepositoryPort userAddressRepository,
            CardRepositoryPort cardRepository,
            InventoryReservationRepositoryPort inventoryReservationRepository) {
        return new CheckoutActionService(
                checkoutSessionRepository,
                cartRepository,
                orderRepository,
                userAddressRepository,
                cardRepository,
                inventoryReservationRepository);
    }

    @Bean
    public OrderUseCase orderService(
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
        return new OrderService(
                orderRepo,
                notificationService,
                checkoutSessionRepository,
                cartRepository,
                productRepository,
                inventoryRepository,
                inventoryReservationRepository,
                checkoutMapper,
                orderMapper,
                checkoutActionService,
                paymentService);
    }

    @Bean
    public UserUseCase userService(
            UserRepositoryPort userRepo,
            UserAddressRepositoryPort addressRepo,
            UserMapper userMapper) {
        return new UserService(userRepo, addressRepo, userMapper);
    }

    @Bean
    public AuthUseCase authService(
            UserRepositoryPort userRepo,
            RefreshTokenRepositoryPort refreshRepo,
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder) {
        return new AuthService(userRepo, refreshRepo, jwtUtil, passwordEncoder);
    }
}
