![alt text](image-3.png)

1. Problem / Why it's importantInventory là nơi race condition xảy ra nhiều nhất trong e-commerce. Scenario cụ thể:Stock: 1 item còn lại
Thread A: đọc quantity = 1 → đủ hàng
Thread B: đọc quantity = 1 → đủ hàng
Thread A: UPDATE quantity = 0 ✅
Thread B: UPDATE quantity = -1 ✅ ← OVERSELLCó 3 cách giải quyết: Pessimistic Locking, Optimistic Locking, Atomic UPDATE. Mỗi cái có trade-off khác nhau. Interviewer tại Grab/Shopee sẽ hỏi tại sao chọn cái này mà không chọn cái kia.2. Cơ chế hoạt động — Bản chất trước, code sauOptimistic Locking dựa trên giả định: conflict ít xảy ra, nên không lock row khi đọc. Chỉ check conflict lúc write.Cơ chế:

Mỗi row có 1 version column
Khi đọc: lấy version hiện tại
Khi write: UPDATE ... WHERE version = :version_lúc_đọc
Nếu ai đó đã update trước → version đã tăng → WHERE không match → affected rows = 0 → conflict detected

3. Code — Từng layer, runnablePostgreSQL SchemasqlCREATE TABLE inventory (
    product_id  UUID        PRIMARY KEY,
    quantity    INT         NOT NULL CHECK (quantity >= 0),
    version     BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Test data
INSERT INTO inventory VALUES
  ('prod-001', 1, 5, NOW()); -- quantity=1, version=5Khi JPA thực thi update, câu SQL thực sự được sinh ra là:sql-- JPA @Version tự sinh ra WHERE clause này
UPDATE inventory
SET quantity   = 0,
    version    = 6,        -- tăng version
    updated_at = NOW()
WHERE product_id = 'prod-001'
  AND version    = 5;      -- check version lúc đọc

-- affected rows = 0 → JPA throw OptimisticLockExceptionDomain Layer — Inventory Aggregatejava// Inventory.java — pure domain, zero Spring/JPA
public class Inventory {
    private final UUID productId;
    private int quantity;
    private long version;

    // Factory
    public static Inventory of(UUID productId, int quantity, long version) {
        if (quantity < 0) throw new DomainException("Quantity cannot be negative");
        return new Inventory(productId, quantity, version);
    }

    // Reserve: giảm quantity, throw nếu không đủ
    public ReservationResult reserve(int requested) {
        if (requested <= 0) {
            throw new DomainException("Requested quantity must be positive");
        }
        if (this.quantity < requested) {
            return ReservationResult.insufficient(productId, quantity, requested);
        }
        this.quantity -= requested;
        return ReservationResult.success(productId, requested);
    }

    // Release: hoàn trả quantity (compensating transaction)
    public void release(int quantity) {
        if (quantity <= 0) throw new DomainException("Release quantity must be positive");
        this.quantity += quantity;
    }

    public boolean hasSufficientStock(int requested) {
        return this.quantity >= requested;
    }

    // Getters
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public long getVersion() { return version; }
}JPA Entity — Tách khỏi Domainjava// InventoryJpaEntity.java
@Entity
@Table(name = "inventory")
public class InventoryJpaEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Version  // ← JPA tự handle: thêm WHERE version=? vào UPDATE, tăng version sau mỗi save
    private Long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    // Mapping
    public static InventoryJpaEntity fromDomain(Inventory inv) {
        var entity = new InventoryJpaEntity();
        entity.productId = inv.getProductId();
        entity.quantity  = inv.getQuantity();
        entity.version   = inv.getVersion();
        return entity;
    }

    public Inventory toDomain() {
        return Inventory.of(productId, quantity, version);
    }
}Application Service — Transaction Boundary + Retryjava// InventoryApplicationService.java
@Service
@RequiredArgsConstructor
public class InventoryApplicationService {

    private final InventoryRepository inventoryRepository;
    private static final int MAX_RETRY = 3;

    /**
     * Reserve inventory với retry on optimistic lock conflict.
     * Retry hợp lý vì: conflict = người khác vừa update,
     * re-read sẽ thấy stock mới nhất và quyết định lại.
     */
    public ReservationResult reserve(UUID productId, int quantity) {
        int attempt = 0;

        while (attempt < MAX_RETRY) {
            try {
                return doReserve(productId, quantity);

            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                attempt++;
                log.warn("Optimistic lock conflict on product={}, attempt={}/{}",
                    productId, attempt, MAX_RETRY);

                if (attempt >= MAX_RETRY) {
                    // Sau 3 lần vẫn conflict → hệ thống đang bị load rất cao
                    // Trả về lỗi thay vì loop vô hạn
                    throw new InventoryConflictException(
                        "Too many concurrent requests for product: " + productId
                    );
                }

                // Backoff nhỏ để giảm thundering herd
                sleepMillis(50L * attempt); // 50ms, 100ms, 150ms
            }
        }

        throw new InventoryConflictException("Unreachable"); // compiler happy
    }

    @Transactional
    private ReservationResult doReserve(UUID productId, int quantity) {
        // SELECT — không dùng FOR UPDATE, không lock row
        Inventory inventory = inventoryRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // Domain logic — throw nếu không đủ stock
        ReservationResult result = inventory.reserve(quantity);

        if (result.isSuccess()) {
            // JPA save → sinh UPDATE WHERE version=? → tăng version
            // Nếu version mismatch → OptimisticLockException → caught ở caller
            inventoryRepository.save(inventory);
        }

        return result;
    }

    /**
     * Compensating transaction — release inventory khi Saga rollback.
     * Cũng cần retry vì release cũng có thể conflict.
     */
    @Transactional
    @Retryable(
        retryFor = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50)
    )
    public void release(UUID productId, int quantity) {
        Inventory inventory = inventoryRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        inventory.release(quantity);
        inventoryRepository.save(inventory);

        log.info("Released {} units for product={}", quantity, productId);
    }

    private void sleepMillis(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}Advanced: Atomic UPDATE — Không cần @VersionCó 1 approach khác mạnh hơn trong nhiều trường hợp: bỏ qua @Version, dùng atomic SQL UPDATE trực tiếp:java// InventoryRepository.java
public interface InventoryRepository extends JpaRepository<InventoryJpaEntity, UUID> {

    /**
     * Atomic reserve — 1 câu SQL duy nhất, không cần optimistic locking.
     * UPDATE trả về affected rows — nếu = 0 thì không đủ stock hoặc conflict.
     */
    @Modifying
    @Query("""
        UPDATE inventory
        SET quantity  = quantity - :requested,
            version   = version + 1,
            updated_at = NOW()
        WHERE product_id = :productId
          AND quantity   >= :requested
        """)
    int atomicReserve(
        @Param("productId") UUID productId,
        @Param("requested") int requested
    );
}java// Dùng trong service
@Transactional
public ReservationResult atomicReserve(UUID productId, int quantity) {
    int affected = inventoryRepository.atomicReserve(productId, quantity);

    if (affected == 0) {
        // Không biết lý do: hết hàng hay concurrent conflict
        // Cần query lại để biết tại sao
        int currentQty = inventoryRepository.findQuantityById(productId);
        if (currentQty < quantity) {
            return ReservationResult.insufficient(productId, currentQty, quantity);
        }
        // Nếu qty đủ mà affected=0 → race condition (hiếm gặp với approach này)
        throw new InventoryConflictException("Concurrent modification detected");
    }

    return ReservationResult.success(productId, quantity);
}Atomic UPDATE không cần retry vì DB engine handle race condition ngay trong 1 statement.4. So sánh 3 ApproachesOption A: Pessimistic Locking — SELECT FOR UPDATEsql-- Lock row ngay khi đọc
SELECT * FROM inventory
WHERE product_id = :id
FOR UPDATE;  -- row bị lock cho đến khi transaction commit/rollbackjava@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<InventoryJpaEntity> findByProductId(UUID productId);
Advantages: Đơn giản, không cần retry, guarantee no conflict
Disadvantages: Lock row trong suốt thời gian xử lý → throughput thấp, deadlock risk nếu nhiều resource
When to use: Conflict rate cao (>30%), critical financial operations, short transactions
When to avoid: High-traffic inventory, long-running transactions
Option B: Optimistic Locking — @Version (approach chính trong bài)

Advantages: Không lock, throughput cao, scale tốt khi conflict rate thấp
Disadvantages: Cần retry logic, không tốt khi conflict rate cao (retry loop vô ích)
When to use: Conflict rate thấp (<10%), read-heavy systems, horizontal scaling
When to avoid: Flash sale (10.000 người tranh 1 item) → conflict rate 99% → retry vô ích
Option C: Atomic UPDATE (best for inventory)

Advantages: 1 round-trip DB, không cần version column, không cần retry, throughput cao nhất
Disadvantages: Logic phức tạp hơn khi cần biết lý do fail, không dùng được với JPA entity thông thường
When to use: Inventory reservation, counter increment — bất kỳ operation nào chỉ cần tăng/giảm số
When to avoid: Khi business logic phức tạp phải đọc state trước khi quyết định
5. Common Mistakes / TrapsTrap 1: Không handle affected rows = 0 sau atomic UPDATE
java// WRONG — im lặng khi update fail
inventoryRepository.atomicReserve(productId, qty);

// RIGHT — check và handle
int affected = inventoryRepository.atomicReserve(productId, qty);
if (affected == 0) { /* handle */ }Trap 2: Retry vô hạn
java// WRONG — flash sale scenario: 10.000 thread đều retry liên tục
while (true) {
    try { doReserve(); break; }
    catch (OptimisticLockException e) { continue; } // thundering herd
}

// RIGHT — max retry + backoff + circuit breakerTrap 3: @Version trên collection field
java// WRONG — version chỉ track thay đổi của root entity
// Nếu thay đổi @OneToMany collection mà không thay đổi root → version không tăng
@Version
private Long version; // chỉ track inventory fields, không track sub-collectionsTrap 4: Flash sale với Optimistic LockingFlash sale: 10.000 user, 100 items.
Thread 1-100: thành công
Thread 101-10.000: tất cả OptimisticLockException → retry → conflict tiếp → CPU spikeFlash sale cần queue-based approach: nhận request vào queue, process tuần tự, không retry.Trap 5: CHECK (quantity >= 0) thiếu
sql-- Không có CHECK constraint → DB cho phép quantity âm
-- Application bug vẫn có thể oversell dù có optimistic locking
ALTER TABLE inventory ADD CONSTRAINT chk_qty CHECK (quantity >= 0);
-- DB là last line of defense6. Optimal Advice cho Bảo-sanTrong project này: Dùng Atomic UPDATE cho reservation, @Version cho các operations khác (update product info, restock). Đây là cách production systems thực sự làm — không phải chọn 1 approach cho tất cả.Prove nó hoạt động — viết concurrent test:java@SpringBootTest
class InventoryServiceConcurrentTest {

    @Autowired InventoryApplicationService inventoryService;
    @Autowired InventoryRepository inventoryRepository;

    @Test
    void shouldNotOversell_whenConcurrentRequests() throws InterruptedException {
        // Setup: 1 item còn lại
        inventoryRepository.save(new InventoryJpaEntity("prod-001", 1));

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount   = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.reserve(UUID.fromString("prod-001"), 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException | InventoryConflictException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // Assert: chính xác 1 success, 99 fail
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);

        // DB quantity phải là 0, không bao giờ âm
        int finalQty = inventoryRepository.findQuantityById(UUID.fromString("prod-001"));
        assertThat(finalQty).isEqualTo(0);
    }
}