package com.ecommerce.monolith.common.init;

import com.ecommerce.monolith.common.status.CartStatus;
import com.ecommerce.monolith.common.status.CheckoutSessionStatus;
import com.ecommerce.monolith.common.status.InventoryReservationStatus;
import com.ecommerce.monolith.common.status.OrderStatus;
import com.ecommerce.monolith.domain.cart.entity.Cart;
import com.ecommerce.monolith.domain.cart.entity.CartItem;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import com.ecommerce.monolith.domain.inventory.entity.Inventory;
import com.ecommerce.monolith.domain.inventory.entity.InventoryReservation;
import com.ecommerce.monolith.domain.inventory.repository.InventoryRepository;
import com.ecommerce.monolith.domain.inventory.repository.InventoryReservationRepository;
import com.ecommerce.monolith.domain.notification.entity.NotificationEntity;
import com.ecommerce.monolith.domain.notification.repository.NotificationRepository;
import com.ecommerce.monolith.domain.order.entity.CheckoutSession;
import com.ecommerce.monolith.domain.order.entity.Order;
import com.ecommerce.monolith.domain.order.entity.OrderItem;
import com.ecommerce.monolith.domain.order.repository.CheckoutSessionRepository;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.payment.entity.PaymentEntity;
import com.ecommerce.monolith.domain.payment.repository.PaymentRepository;
import com.ecommerce.monolith.domain.user.entity.RefreshTokenEntity;
import com.ecommerce.monolith.domain.user.entity.UserAddressEntity;
import com.ecommerce.monolith.domain.user.entity.UserEntity;
import com.ecommerce.monolith.domain.user.repository.RefreshTokenRepository;
import com.ecommerce.monolith.domain.user.repository.UserAddressRepository;
import com.ecommerce.monolith.domain.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(
            UserRepository userRepository,
            UserAddressRepository userAddressRepository,
            RefreshTokenRepository refreshTokenRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            CartRepository cartRepository,
            OrderRepository orderRepository,
            CheckoutSessionRepository checkoutSessionRepository,
            PaymentRepository paymentRepository,
            InventoryReservationRepository inventoryReservationRepository,
            NotificationRepository notificationRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (productRepository.count() > 0 || userRepository.count() > 0) {
            System.out.println("Database already contains data. Skipping database seeding.");
            return;
        }

        System.out.println("Starting database seeding for all entities...");

        // 1. Create Users
        String defaultHashedPassword = passwordEncoder.encode("secret123");

        UserEntity regularUser = UserEntity.create("user@ecommerce.com", defaultHashedPassword, "John Doe", "0987654321");
        userRepository.save(regularUser);

        UserEntity adminUser = UserEntity.create("admin@ecommerce.com", defaultHashedPassword, "Admin User", "1234567890");
        adminUser.setRoles("USER,ADMIN");
        userRepository.save(adminUser);

        // 2. Create User Addresses
        UserAddressEntity userHomeAddress = UserAddressEntity.create(
                regularUser, "Home", "123 Main St", "Hanoi", "100000", "Vietnam", true
        );
        UserAddressEntity userOfficeAddress = UserAddressEntity.create(
                regularUser, "Office", "456 Tech Park", "Ho Chi Minh City", "700000", "Vietnam", false
        );
        UserAddressEntity adminHQAddress = UserAddressEntity.create(
                adminUser, "HQ", "1 Admin Way", "Da Nang", "550000", "Vietnam", true
        );
        userAddressRepository.saveAll(Arrays.asList(userHomeAddress, userOfficeAddress, adminHQAddress));

        // 3. Create Refresh Tokens
        RefreshTokenEntity activeToken = RefreshTokenEntity.create(
                regularUser, "active_token_hash_value_1234567890", 24 * 60 * 60 * 1000L, "Chrome - Windows 11"
        );
        RefreshTokenEntity revokedToken = RefreshTokenEntity.create(
                regularUser, "revoked_token_hash_value_0987654321", 24 * 60 * 60 * 1000L, "Safari - macOS"
        );
        revokedToken.revoke();
        refreshTokenRepository.saveAll(Arrays.asList(activeToken, revokedToken));

        // 4. Create Products
        Product macbook = new Product();
        macbook.setSku("MAC-PRO-16");
        macbook.setName("MacBook Pro 16-inch");
        macbook.setDescription("Apple M3 Max chip, 36GB RAM, 1TB SSD");
        macbook.setPrice(new BigDecimal("2499.00"));
        macbook.setActive(true);

        Product dellXps = new Product();
        dellXps.setSku("DELL-XPS-15");
        dellXps.setName("Dell XPS 15");
        dellXps.setDescription("Intel Core i9, 32GB RAM, 1TB SSD, RTX 4060");
        dellXps.setPrice(new BigDecimal("1899.00"));
        dellXps.setActive(true);

        Product keyboard = new Product();
        keyboard.setSku("KBD-MECH-87");
        keyboard.setName("Mechanical Keyboard");
        keyboard.setDescription("Tenkeyless mechanical keyboard with brown switches");
        keyboard.setPrice(new BigDecimal("129.99"));
        keyboard.setActive(true);

        Product mouse = new Product();
        mouse.setSku("MSE-WRLS-ERG");
        mouse.setName("Wireless Ergonomic Mouse");
        mouse.setDescription("Ergonomic multi-device wireless mouse");
        mouse.setPrice(new BigDecimal("79.99"));
        mouse.setActive(true);

        Product monitor = new Product();
        monitor.setSku("MON-UW-34");
        monitor.setName("UltraWide Monitor 34-inch");
        monitor.setDescription("34-inch curved WQHD monitor, 144Hz refresh rate");
        monitor.setPrice(new BigDecimal("499.99"));
        monitor.setActive(true);

        Product headphones = new Product();
        headphones.setSku("HD-ANC-900");
        headphones.setName("Noise Cancelling Headphones");
        headphones.setDescription("Active noise cancelling wireless over-ear headphones");
        headphones.setPrice(new BigDecimal("299.99"));
        headphones.setActive(true);

        Product usbAdapter = new Product();
        usbAdapter.setSku("ADP-USBC-8IN1");
        usbAdapter.setName("USB-C Multi-port Adapter");
        usbAdapter.setDescription("8-in-1 USB-C hub with HDMI, Ethernet, USB 3.0");
        usbAdapter.setPrice(new BigDecimal("49.99"));
        usbAdapter.setActive(true);

        productRepository.saveAll(Arrays.asList(macbook, dellXps, keyboard, mouse, monitor, headphones, usbAdapter));

        // 5. Create Inventories
        Inventory invMacbook = Inventory.create(macbook.getId(), 50);
        Inventory invDellXps = Inventory.create(dellXps.getId(), 50);
        Inventory invKeyboard = Inventory.create(keyboard.getId(), 100);
        Inventory invMouse = Inventory.create(mouse.getId(), 100);
        Inventory invMonitor = Inventory.create(monitor.getId(), 75);
        Inventory invHeadphones = Inventory.create(headphones.getId(), 80);
        Inventory invUsbAdapter = Inventory.create(usbAdapter.getId(), 150);

        inventoryRepository.saveAll(Arrays.asList(
                invMacbook, invDellXps, invKeyboard, invMouse, invMonitor, invHeadphones, invUsbAdapter
        ));

        // 6. Carts & Cart Items
        // Cart 1: Active cart for regular user with keyboard and mouse
        Cart activeCart = Cart.create(regularUser.getId());
        cartRepository.save(activeCart);

        CartItem item1 = CartItem.create(activeCart, keyboard.getId(), keyboard.getName(), 1, keyboard.getPrice());
        CartItem item2 = CartItem.create(activeCart, mouse.getId(), mouse.getName(), 2, mouse.getPrice());
        activeCart.getItems().add(item1);
        activeCart.getItems().add(item2);
        cartRepository.save(activeCart);

        // Cart 2: Abandoned cart for regular user with USB adapter
        Cart abandonedCart = Cart.create(regularUser.getId());
        abandonedCart.markAbandoned();
        cartRepository.save(abandonedCart);

        CartItem item3 = CartItem.create(abandonedCart, usbAdapter.getId(), usbAdapter.getName(), 1, usbAdapter.getPrice());
        abandonedCart.getItems().add(item3);
        cartRepository.save(abandonedCart);

        // 7. Orders & Order Items
        // Order 1: Completed Order (Keyboard + Headphone)
        UUID order1IdempotencyKey = UUID.randomUUID();
        BigDecimal order1Total = keyboard.getPrice().add(headphones.getPrice().multiply(BigDecimal.valueOf(2)));
        Order order1 = Order.builder()
                .userId(regularUser.getId())
                .status(OrderStatus.COMPLETED)
                .totalAmount(order1Total)
                .idempotencyKey(order1IdempotencyKey)
                .shippingAddressId(userHomeAddress.getId())
                .notes("Deliver after 5 PM")
                .createdAt(Instant.now().minusSeconds(3600 * 24 * 2)) // 2 days ago
                .updatedAt(Instant.now().minusSeconds(3600 * 24 * 2))
                .items(new ArrayList<>())
                .build();
        orderRepository.save(order1);

        OrderItem o1Item1 = OrderItem.create(order1, keyboard.getId(), keyboard.getName(), 1, keyboard.getPrice());
        OrderItem o1Item2 = OrderItem.create(order1, headphones.getId(), headphones.getName(), 2, headphones.getPrice());
        order1.getItems().add(o1Item1);
        order1.getItems().add(o1Item2);
        orderRepository.save(order1);

        // Order 2: Pending Order (Dell XPS)
        UUID order2IdempotencyKey = UUID.randomUUID();
        Order order2 = Order.builder()
                .userId(regularUser.getId())
                .status(OrderStatus.PENDING)
                .totalAmount(dellXps.getPrice())
                .idempotencyKey(order2IdempotencyKey)
                .shippingAddressId(userHomeAddress.getId())
                .notes("Handle with care")
                .createdAt(Instant.now().minusSeconds(1800)) // 30 mins ago
                .updatedAt(Instant.now().minusSeconds(1800))
                .items(new ArrayList<>())
                .build();
        orderRepository.save(order2);

        OrderItem o2Item1 = OrderItem.create(order2, dellXps.getId(), dellXps.getName(), 1, dellXps.getPrice());
        order2.getItems().add(o2Item1);
        orderRepository.save(order2);

        // 8. Checkout Sessions
        CheckoutSession cs1 = CheckoutSession.builder()
                .idempotencyKey(order1IdempotencyKey)
                .userId(regularUser.getId())
                .cartId(abandonedCart.getId())
                .totalAmount(order1Total)
                .status(CheckoutSessionStatus.SUCCESS)
                .responseBody("{\"status\":\"SUCCESS\",\"orderId\":\"" + order1.getId() + "\"}")
                .build();

        CheckoutSession cs2 = CheckoutSession.builder()
                .idempotencyKey(order2IdempotencyKey)
                .userId(regularUser.getId())
                .cartId(activeCart.getId())
                .totalAmount(dellXps.getPrice())
                .status(CheckoutSessionStatus.SUCCESS)
                .responseBody("{\"status\":\"SUCCESS\",\"orderId\":\"" + order2.getId() + "\"}")
                .build();
        checkoutSessionRepository.saveAll(Arrays.asList(cs1, cs2));

        // 9. Payments
        // Payment for Order 1: Successful payment
        PaymentEntity payment1 = PaymentEntity.create(order1.getId(), regularUser.getId(), order1Total);
        payment1.markCharged();
        paymentRepository.save(payment1);

        // Failed Payment Attempt for Order 2
        PaymentEntity failedPayment = PaymentEntity.create(order2.getId(), regularUser.getId(), dellXps.getPrice());
        failedPayment.markFailed("Insufficient funds on card");
        paymentRepository.save(failedPayment);

        // 10. Inventory Reservations
        // Succeeded reservation for Order 2 (which is pending)
        Instant reservationExpiry = Instant.now().plusSeconds(1800); // expires in 30 mins
        InventoryReservation reservation = InventoryReservation.reserved(
                order2.getId(), dellXps.getId(), 1, reservationExpiry
        );
        inventoryReservationRepository.save(reservation);

        // 11. Notifications
        // Confirmed notification for Order 1
        NotificationEntity notification = NotificationEntity.create(
                order1.getId(), regularUser.getId(), "ORDER_CONFIRMED", regularUser.getEmail(), "Order Confirmed: #" + order1.getId()
        );
        notification.markSent();
        notificationRepository.save(notification);

        System.out.println("Database seeding completed successfully.");
    }
}
