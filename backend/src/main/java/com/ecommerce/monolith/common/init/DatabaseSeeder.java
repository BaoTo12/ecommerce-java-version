package com.ecommerce.monolith.common.init;

import com.ecommerce.monolith.domain.cart.entity.Cart;
import com.ecommerce.monolith.domain.cart.entity.CartItem;
import com.ecommerce.monolith.domain.cart.repository.CartRepository;
import com.ecommerce.monolith.domain.catalog.entity.Product;
import com.ecommerce.monolith.domain.catalog.repository.ProductRepository;
import com.ecommerce.monolith.domain.inventory.entity.Inventory;
import com.ecommerce.monolith.domain.inventory.repository.InventoryRepository;
import com.ecommerce.monolith.domain.inventory.repository.InventoryReservationRepository;
import com.ecommerce.monolith.domain.notification.repository.NotificationRepository;
import com.ecommerce.monolith.domain.order.repository.CheckoutSessionRepository;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.payment.repository.PaymentRepository;
import com.ecommerce.monolith.domain.user.entity.Card;
import com.ecommerce.monolith.domain.user.entity.RefreshToken;
import com.ecommerce.monolith.domain.user.entity.User;
import com.ecommerce.monolith.domain.user.entity.UserAddress;
import com.ecommerce.monolith.domain.user.repository.CardRepository;
import com.ecommerce.monolith.domain.user.repository.RefreshTokenRepository;
import com.ecommerce.monolith.domain.user.repository.UserAddressRepository;
import com.ecommerce.monolith.domain.user.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
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
    private final CardRepository cardRepository;

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

        User regularUser =
                User.builder()
                        .email("user@ecommerce.com")
                        .hashedPassword(defaultHashedPassword)
                        .name("John Doe")
                        .phone("0987654321")
                        .build();
        userRepository.save(regularUser);

        User adminUser =
                User.builder()
                        .email("admin@ecommerce.com")
                        .hashedPassword(defaultHashedPassword)
                        .name("Admin User")
                        .phone("1234567890")
                        .roles("USER,ADMIN")
                        .build();
        userRepository.save(adminUser);

        // 2. Create User Addresses
        UserAddress userHomeAddress =
                UserAddress.builder()
                        .user(regularUser)
                        .label("Home")
                        .addressLine1("123 Main St")
                        .city("Hanoi")
                        .postalCode("100000")
                        .country("Vietnam")
                        .isDefault(true)
                        .build();
        UserAddress userOfficeAddress =
                UserAddress.builder()
                        .user(regularUser)
                        .label("Office")
                        .addressLine1("456 Tech Park")
                        .city("Ho Chi Minh City")
                        .postalCode("700000")
                        .country("Vietnam")
                        .isDefault(false)
                        .build();
        UserAddress adminHQAddress =
                UserAddress.builder()
                        .user(adminUser)
                        .label("HQ")
                        .addressLine1("1 Admin Way")
                        .city("Da Nang")
                        .postalCode("550000")
                        .country("Vietnam")
                        .isDefault(true)
                        .build();
        userAddressRepository.saveAll(
                Arrays.asList(userHomeAddress, userOfficeAddress, adminHQAddress));

        // 3. Create Refresh Tokens
        RefreshToken activeToken =
                RefreshToken.builder()
                        .user(regularUser)
                        .tokenHash("active_token_hash_value_1234567890")
                        .expirationMs(24 * 60 * 60 * 1000L)
                        .deviceName("Chrome - Windows 11")
                        .build();
        RefreshToken revokedToken =
                RefreshToken.builder()
                        .user(regularUser)
                        .tokenHash("revoked_token_hash_value_0987654321")
                        .expirationMs(24 * 60 * 60 * 1000L)
                        .deviceName("Safari - macOS")
                        .build();
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

        productRepository.saveAll(
                Arrays.asList(macbook, dellXps, keyboard, mouse, monitor, headphones, usbAdapter));

        // 5. Create Inventories
        Inventory invMacbook = Inventory.builder().productId(macbook.getId()).quantity(50).build();
        Inventory invDellXps = Inventory.builder().productId(dellXps.getId()).quantity(50).build();
        Inventory invKeyboard = Inventory.builder().productId(keyboard.getId()).quantity(100).build();
        Inventory invMouse = Inventory.builder().productId(mouse.getId()).quantity(100).build();
        Inventory invMonitor = Inventory.builder().productId(monitor.getId()).quantity(75).build();
        Inventory invHeadphones =
                Inventory.builder().productId(headphones.getId()).quantity(80).build();
        Inventory invUsbAdapter =
                Inventory.builder().productId(usbAdapter.getId()).quantity(150).build();

        inventoryRepository.saveAll(
                Arrays.asList(
                        invMacbook,
                        invDellXps,
                        invKeyboard,
                        invMouse,
                        invMonitor,
                        invHeadphones,
                        invUsbAdapter));

        // 6. Carts & Cart Items
        // Active cart for regular user with keyboard and mouse
        Cart activeCart = Cart.builder().userId(regularUser.getId()).build();
        cartRepository.save(activeCart);

        CartItem item1 =
                CartItem.builder()
                        .cart(activeCart)
                        .productId(keyboard.getId())
                        .productName(keyboard.getName())
                        .quantity(1)
                        .priceSnapshot(keyboard.getPrice())
                        .build();
        CartItem item2 =
                CartItem.builder()
                        .cart(activeCart)
                        .productId(mouse.getId())
                        .productName(mouse.getName())
                        .quantity(2)
                        .priceSnapshot(mouse.getPrice())
                        .build();
        activeCart.getItems().add(item1);
        activeCart.getItems().add(item2);
        cartRepository.save(activeCart);

        // 7. Seed Default User Cards
        Card defaultCard =
                Card.builder()
                        .user(regularUser)
                        .cardNumber("4242424242424242")
                        .cvc("123")
                        .cardName("John Doe")
                        .expiry("12/28")
                        .isDefault(true)
                        .build();
        cardRepository.save(defaultCard);

        Card adminCard =
                Card.builder()
                        .user(adminUser)
                        .cardNumber("4242424242424242")
                        .cvc("123")
                        .cardName("Admin User")
                        .expiry("12/28")
                        .isDefault(false)
                        .build();
        cardRepository.save(adminCard);

        System.out.println("Database seeding completed successfully.");
    }
}
