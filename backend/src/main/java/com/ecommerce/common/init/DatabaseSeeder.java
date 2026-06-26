package com.ecommerce.common.init;

import com.ecommerce.domain.cart.adapter.out.persistence.CartItemJpaEntity;
import com.ecommerce.domain.cart.adapter.out.persistence.CartJpaEntity;
import com.ecommerce.domain.cart.adapter.out.persistence.SpringDataCartRepository;
import com.ecommerce.domain.catalog.adapter.out.persistence.ProductJpaEntity;
import com.ecommerce.domain.catalog.adapter.out.persistence.SpringDataProductRepository;
import com.ecommerce.domain.inventory.adapter.out.persistence.InventoryJpaEntity;
import com.ecommerce.domain.inventory.adapter.out.persistence.SpringDataInventoryRepository;
import com.ecommerce.domain.user.adapter.out.persistence.CardJpaEntity;
import com.ecommerce.domain.user.adapter.out.persistence.RefreshTokenJpaEntity;
import com.ecommerce.domain.user.adapter.out.persistence.UserAddressJpaEntity;
import com.ecommerce.domain.user.adapter.out.persistence.UserJpaEntity;
import com.ecommerce.domain.user.adapter.out.persistence.SpringDataCardRepository;
import com.ecommerce.domain.user.adapter.out.persistence.SpringDataRefreshTokenRepository;
import com.ecommerce.domain.user.adapter.out.persistence.SpringDataUserAddressRepository;
import com.ecommerce.domain.user.adapter.out.persistence.SpringDataUserRepository;

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

    private final SpringDataUserRepository userRepository;
    private final SpringDataUserAddressRepository userAddressRepository;
    private final SpringDataRefreshTokenRepository refreshTokenRepository;
    private final SpringDataProductRepository productRepository;
    private final SpringDataInventoryRepository inventoryRepository;
    private final SpringDataCartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final SpringDataCardRepository cardRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("Starting database seeding/verification...");

        // 1. Seed or Update Base Products (originally 28 products)
        seedOrUpdateProduct("MAC-PRO-16", "MacBook Pro 16-inch", "Apple M3 Max chip, 36GB RAM, 1TB SSD", new BigDecimal("64990000.00"), "Laptops & Computers", 50);
        seedOrUpdateProduct("DELL-XPS-15", "Dell XPS 15", "Intel Core i9, 32GB RAM, 1TB SSD, RTX 4060", new BigDecimal("48990000.00"), "Laptops & Computers", 50);
        seedOrUpdateProduct("MON-UW-34", "UltraWide Monitor 34-inch", "34-inch curved WQHD monitor, 144Hz refresh rate", new BigDecimal("11990000.00"), "Laptops & Computers", 75);
        seedOrUpdateProduct("ASUS-ROG-14", "Asus ROG Zephyrus G14", "ROG Nebula Display, AMD Ryzen 9, 32GB RAM, 1TB SSD, RTX 4070", new BigDecimal("54990000.00"), "Laptops & Computers", 40);
        seedOrUpdateProduct("THINKPAD-X1", "Lenovo ThinkPad X1 Carbon", "Ultralight business laptop, Intel Core i7, 16GB RAM, 512GB SSD", new BigDecimal("42990000.00"), "Laptops & Computers", 45);

        seedOrUpdateProduct("PHONE-IP15", "iPhone 15 Pro Max", "Apple iPhone 15 Pro Max 256GB - Premium titanium design", new BigDecimal("34990000.00"), "Phones & Tablets", 120);
        seedOrUpdateProduct("PHONE-SS24", "Samsung Galaxy S24 Ultra", "Samsung Galaxy S24 Ultra 512GB - AI Camera integration", new BigDecimal("31990000.00"), "Phones & Tablets", 100);
        seedOrUpdateProduct("TABLET-IPD", "iPad Pro 12.9\"", "Apple iPad Pro 12.9-inch Liquid Retina XDR M2 chip", new BigDecimal("28990000.00"), "Phones & Tablets", 90);
        seedOrUpdateProduct("PHONE-PIX8", "Google Pixel 8 Pro", "Google Pixel 8 Pro 128GB - Obsidian, advanced AI Google camera", new BigDecimal("22490000.00"), "Phones & Tablets", 65);
        seedOrUpdateProduct("PHONE-OP12", "OnePlus 12", "OnePlus 12 256GB Silky Black, 16GB RAM, Snapdragon 8 Gen 3", new BigDecimal("18990000.00"), "Phones & Tablets", 70);

        seedOrUpdateProduct("KBD-MECH-87", "Mechanical Keyboard", "Tenkeyless mechanical keyboard with brown switches", new BigDecimal("2990000.00"), "Audio & Accessories", 100);
        seedOrUpdateProduct("MSE-WRLS-ERG", "Wireless Ergonomic Mouse", "Ergonomic multi-device wireless mouse", new BigDecimal("1890000.00"), "Audio & Accessories", 100);
        seedOrUpdateProduct("HD-ANC-900", "Noise Cancelling Headphones", "Active noise cancelling wireless over-ear headphones", new BigDecimal("6990000.00"), "Audio & Accessories", 80);
        seedOrUpdateProduct("ADP-USBC-8IN1", "USB-C Multi-port Adapter", "8-in-1 USB-C hub with HDMI, Ethernet, USB 3.0", new BigDecimal("990000.00"), "Audio & Accessories", 150);
        seedOrUpdateProduct("HEADPHONE-APM", "AirPods Max", "Apple AirPods Max - Space Gray high-fidelity over-ear audio", new BigDecimal("13490000.00"), "Audio & Accessories", 150);
        seedOrUpdateProduct("WATCH-ULTRA", "Apple Watch Ultra 2", "Rugged adventure watch with bright Always-On Retina display", new BigDecimal("22990000.00"), "Audio & Accessories", 110);
        seedOrUpdateProduct("BOSE-QC-ULTRA", "Bose QuietComfort Ultra", "Spatial audio wireless noise cancelling over-ear headphones", new BigDecimal("9490000.00"), "Audio & Accessories", 85);
        seedOrUpdateProduct("SONY-WF5", "Sony WF-1000XM5", "Premium noise cancelling wireless earbuds with high-res audio", new BigDecimal("5990000.00"), "Audio & Accessories", 120);

        seedOrUpdateProduct("CAM-GOPRO", "GoPro Hero 12 Black", "Ultra-versatile action camera with HyperSmooth stabilization", new BigDecimal("10490000.00"), "Cameras & Drones", 85);
        seedOrUpdateProduct("DRONE-DJI", "DJI Mini 4 Pro Drone", "Lightweight folding drone with 4K HDR camera & omnidirectional sensing", new BigDecimal("19990000.00"), "Cameras & Drones", 45);
        seedOrUpdateProduct("CAM-CANON", "Canon EOS R5 Camera", "Full-frame mirrorless camera featuring 45MP sensor and 8K video capture", new BigDecimal("89990000.00"), "Cameras & Drones", 30);
        seedOrUpdateProduct("CAM-SONYA7", "Sony Alpha 7 IV", "Full-frame mirrorless camera, 33MP hybrid photo & video shooter", new BigDecimal("57990000.00"), "Cameras & Drones", 25);
        seedOrUpdateProduct("CAM-OSMO3", "DJI Osmo Pocket 3", "3-axis gimbal stabilizer camera with 1-inch CMOS sensor", new BigDecimal("12990000.00"), "Cameras & Drones", 90);

        seedOrUpdateProduct("GAME-SWITCH", "Nintendo Switch OLED", "Vibrant 7-inch OLED screen, local & online co-op console", new BigDecimal("8990000.00"), "Gaming & Entertainment", 140);
        seedOrUpdateProduct("GAME-PS5", "Sony PlayStation 5", "Lightning fast SSD loading, immersive 3D audio, and 4K gaming", new BigDecimal("14490000.00"), "Gaming & Entertainment", 60);
        seedOrUpdateProduct("KINDLE-PW", "Kindle Paperwhite", "6.8-inch display, adjustable warm light, and up to 10 weeks battery", new BigDecimal("3990000.00"), "Gaming & Entertainment", 200);
        seedOrUpdateProduct("GAME-SDECK", "Steam Deck OLED", "Handheld gaming console with 512GB NVMe SSD, HDR OLED screen", new BigDecimal("16490000.00"), "Gaming & Entertainment", 80);
        seedOrUpdateProduct("VR-QUEST3", "Meta Quest 3", "128GB virtual reality mixed reality headset with high-res display", new BigDecimal("13990000.00"), "Gaming & Entertainment", 55);

        // Dynamically seed products to reach exactly 30 per category
        String[] categories = {
            "Laptops & Computers",
            "Phones & Tablets",
            "Audio & Accessories",
            "Cameras & Drones",
            "Gaming & Entertainment"
        };
        for (String cat : categories) {
            String prefix = cat.split(" ")[0].toUpperCase();
            long count = productRepository.countByCategory(cat);
            long needed = 30 - count;
            for (int i = 1; i <= needed; i++) {
                String sku = prefix + "-GEN-" + i;
                String name = "Premium " + cat.replaceAll(" &.*", "") + " Model " + i;
                String desc = "High-performance dynamic device in the " + cat + " category, model version " + i + ".";
                BigDecimal price = getCategoryPrice(cat, i);
                seedOrUpdateProduct(sku, name, desc, price, cat, 50);
            }
        }

        // 2. Seed Users, Addresses, Carts, Cards if not seeded yet
        if (userRepository.count() == 0) {
            System.out.println("Seeding users, addresses, tokens, cards, and carts...");
            String defaultHashedPassword = passwordEncoder.encode("secret123");

            UserJpaEntity regularUser =
                    UserJpaEntity.builder()
                            .email("user@ecommerce.com")
                            .hashedPassword(defaultHashedPassword)
                            .name("John Doe")
                            .phone("0987654321")
                            .build();
            userRepository.save(regularUser);

            UserJpaEntity adminUser =
                    UserJpaEntity.builder()
                            .email("admin@ecommerce.com")
                            .hashedPassword(defaultHashedPassword)
                            .name("Admin User")
                            .phone("1234567890")
                            .roles("USER,ADMIN")
                            .build();
            userRepository.save(adminUser);

            UserAddressJpaEntity userHomeAddress =
                    UserAddressJpaEntity.builder()
                            .user(regularUser)
                            .label("Home")
                            .addressLine1("123 Main St")
                            .city("Hanoi")
                            .postalCode("100000")
                            .country("Vietnam")
                            .isDefault(true)
                            .build();
            UserAddressJpaEntity userOfficeAddress =
                    UserAddressJpaEntity.builder()
                            .user(regularUser)
                            .label("Office")
                            .addressLine1("456 Tech Park")
                            .city("Ho Chi Minh City")
                            .postalCode("700000")
                            .country("Vietnam")
                            .isDefault(false)
                            .build();
            UserAddressJpaEntity adminHQAddress =
                    UserAddressJpaEntity.builder()
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

            RefreshTokenJpaEntity activeToken =
                    RefreshTokenJpaEntity.builder()
                            .user(regularUser)
                            .tokenHash("active_token_hash_value_1234567890")
                            .expiresAt(java.time.Instant.now().plusMillis(24 * 60 * 60 * 1000L))
                            .deviceName("Chrome - Windows 11")
                            .build();
            RefreshTokenJpaEntity revokedToken =
                    RefreshTokenJpaEntity.builder()
                            .user(regularUser)
                            .tokenHash("revoked_token_hash_value_0987654321")
                            .expiresAt(java.time.Instant.now().plusMillis(24 * 60 * 60 * 1000L))
                            .deviceName("Safari - macOS")
                            .revokedAt(java.time.Instant.now())
                            .build();
            refreshTokenRepository.saveAll(Arrays.asList(activeToken, revokedToken));

            // Carts & Cart Items
            CartJpaEntity activeCart = CartJpaEntity.builder().userId(regularUser.getId()).build();
            cartRepository.save(activeCart);

            ProductJpaEntity keyboard = productRepository.findBySkuAndIsActiveTrue("KBD-MECH-87")
                    .orElseThrow(() -> new IllegalStateException("Keyboard not found"));
            ProductJpaEntity mouse = productRepository.findBySkuAndIsActiveTrue("MSE-WRLS-ERG")
                    .orElseThrow(() -> new IllegalStateException("Mouse not found"));

            CartItemJpaEntity item1 =
                    CartItemJpaEntity.builder()
                            .cart(activeCart)
                            .productId(keyboard.getId())
                            .productName(keyboard.getName())
                            .quantity(1)
                            .priceSnapshot(keyboard.getPrice())
                            .build();
            CartItemJpaEntity item2 =
                    CartItemJpaEntity.builder()
                            .cart(activeCart)
                            .productId(mouse.getId())
                            .productName(mouse.getName())
                            .quantity(2)
                            .priceSnapshot(mouse.getPrice())
                            .build();
            activeCart.getItems().add(item1);
            activeCart.getItems().add(item2);
            cartRepository.save(activeCart);

            // Seed Default User Cards
            CardJpaEntity defaultCard =
                    CardJpaEntity.builder()
                            .user(regularUser)
                            .cardNumber("4242424242424242")
                            .cvc("123")
                            .cardName("John Doe")
                            .expiry("12/28")
                            .isDefault(true)
                            .build();
            cardRepository.save(defaultCard);

            CardJpaEntity adminCard =
                    CardJpaEntity.builder()
                            .user(adminUser)
                            .cardNumber("4242424242424242")
                            .cvc("123")
                            .cardName("Admin User")
                            .expiry("12/28")
                            .isDefault(false)
                            .build();
            cardRepository.save(adminCard);
        }

        System.out.println("Database seeding/verification completed successfully.");
    }

    private void seedOrUpdateProduct(String sku, String name, String description, BigDecimal price, String category, int inventoryQty) {
        ProductJpaEntity product = productRepository.findBySkuAndIsActiveTrue(sku).orElse(null);
        if (product == null) {
            product = new ProductJpaEntity();
            product.setSku(sku);
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);
            product.setCategory(category);
            product.setActive(true);
            productRepository.save(product);

            InventoryJpaEntity inventory = InventoryJpaEntity.builder()
                    .productId(product.getId())
                    .quantity(inventoryQty)
                    .build();
            inventoryRepository.save(inventory);
            System.out.println("Seeded new product: " + sku);
        } else {
            product.setCategory(category);
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);
            productRepository.save(product);
            System.out.println("Verified/Updated existing product: " + sku);
        }
    }

    private BigDecimal getCategoryPrice(String cat, int i) {
        long base = 5000000;
        if (cat.equals("Laptops & Computers")) {
            base = 25000000 + i * 1000000L;
        } else if (cat.equals("Phones & Tablets")) {
            base = 12000000 + i * 800000L;
        } else if (cat.equals("Audio & Accessories")) {
            base = 2000000 + i * 200000L;
        } else if (cat.equals("Cameras & Drones")) {
            base = 15000000 + i * 1500000L;
        } else {
            base = 5000000 + i * 500000L;
        }
        return new BigDecimal(base + ".00");
    }
}
