![alt text](image.png)

Layer 1: Order State Machine
Order không phải là 1 field status = "done". Nó là một state machine với transition rules nghiêm ngặt.
PENDING → CONFIRMED → PAYMENT_PROCESSING → PAID → FULFILLING → COMPLETED
                                         ↘ PAYMENT_FAILED → CANCELLED
                                                           ↗
                              CONFIRMED → CANCELLED (user cancel)


Java implementation — enum-based state machine:
```
// OrderStatus.java
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PAYMENT_PROCESSING,
    PAID,
    PAYMENT_FAILED,
    FULFILLING,
    COMPLETED,
    CANCELLED;

    // Định nghĩa valid transitions — không transition sai là throw exception
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
        PENDING,             Set.of(CONFIRMED, CANCELLED),
        CONFIRMED,           Set.of(PAYMENT_PROCESSING, CANCELLED),
        PAYMENT_PROCESSING,  Set.of(PAID, PAYMENT_FAILED),
        PAID,                Set.of(FULFILLING),
        PAYMENT_FAILED,      Set.of(CANCELLED),
        FULFILLING,          Set.of(COMPLETED),
        COMPLETED,           Set.of(),
        CANCELLED,           Set.of()
    );

    public void validateTransition(OrderStatus next) {
        if (!VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(next)) {
            throw new IllegalStateTransitionException(
                "Cannot transition from %s to %s".formatted(this, next)
            );
        }
    }
}
```

```
// Order.java — aggregate root
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Version  // <-- Optimistic locking tại đây
    private Long version;

    private UUID userId;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private Instant updatedAt;

    // Factory method — không để constructor public
    public static Order create(UUID userId, List<OrderItem> items) {
        var order = new Order();
        order.id = UUID.randomUUID();
        order.userId = userId;
        order.status = OrderStatus.PENDING;
        order.createdAt = Instant.now();
        order.updatedAt = Instant.now();
        // calculate totalAmount from items
        return order;
    }

    // Transition method — validate trước khi set
    public void transitionTo(OrderStatus next) {
        this.status.validateTransition(next);
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
```

Layer 2: Inventory Race Condition — Optimistic Locking
Problem: 1000 request cùng lúc order item còn 1 cái. Không lock → oversell.
Optimistic Locking dùng version column — không lock DB row, chỉ check version khi UPDATE:

```
-- Schema
CREATE TABLE inventory (
    product_id UUID PRIMARY KEY,
    quantity    INT NOT NULL CHECK (quantity >= 0),
    version     BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- UPDATE với version check — nếu ai đó đã update trước, affected rows = 0
UPDATE inventory
SET quantity = quantity - :requested,
    version = version + 1,
    updated_at = NOW()
WHERE product_id = :productId
  AND version = :expectedVersion
  AND quantity >= :requested;
-- nếu affected rows = 0 → conflict → retry hoặc fail
```

```
@Service
public class InventoryService {

    @Transactional
    public ReservationResult reserve(UUID productId, int quantity) {
        // Fetch với SELECT FOR NO KEY UPDATE (nhẹ hơn FOR UPDATE)
        Inventory inv = inventoryRepo.findByProductIdWithLock(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        if (inv.getQuantity() < quantity) {
            return ReservationResult.insufficientStock();
        }

        // JPA @Version tự handle optimistic locking
        // Nếu version mismatch → OptimisticLockException → Spring retry
        inv.reserve(quantity);
        inventoryRepo.save(inv);

        return ReservationResult.success(inv.getProductId(), quantity);
    }
}
```

```
// Inventory.java
@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    private UUID productId;

    private int quantity;

    @Version  // JPA tự động add version check vào UPDATE query
    private Long version;

    public void reserve(int qty) {
        if (this.quantity < qty) throw new InsufficientStockException();
        this.quantity -= qty;
    }

    public void release(int qty) {
        this.quantity += qty;
    }
}
```

```

```

Layer 3: Saga Pattern — Distributed Transaction
Problem: Trừ inventory + charge payment + tạo order = 3 operations ở 3 service. Không có distributed transaction → cần Saga.
Choreography vs Orchestration:
ChoreographyOrchestrationAi điều phốiKhông ai — event-drivenSaga Orchestrator (Order Service)CouplingLooseTighter (orchestrator biết hết)DebugKhó traceDễ trace hơnFit project này❌ Phức tạp hơn✅ Recommended cho fresher team
Orchestration-based Saga — Order Service là orchestrator:

```
@Service
public class OrderSagaOrchestrator {

    // Step 1: Reserve inventory
    // Step 2: Process payment  
    // Step 3: Confirm order
    // Nếu step nào fail → chạy compensating transactions ngược lại

    @Transactional
    public void startSaga(UUID orderId) {
        Order order = orderRepo.findById(orderId).orElseThrow();

        try {
            // Step 1
            order.transitionTo(OrderStatus.CONFIRMED);
            orderRepo.save(order);
            eventPublisher.publish(new InventoryReservationRequestedEvent(orderId, order.getItems()));

        } catch (Exception e) {
            // Compensate: cancel order
            order.transitionTo(OrderStatus.CANCELLED);
            orderRepo.save(order);
            throw e;
        }
    }

    // Listen event từ Inventory Service
    @KafkaListener(topics = "inventory.reservation.result")
    public void onInventoryResult(InventoryReservationResultEvent event) {
        Order order = orderRepo.findById(event.getOrderId()).orElseThrow();

        if (event.isSuccess()) {
            // Step 2: trigger payment
            order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
            orderRepo.save(order);
            eventPublisher.publish(new PaymentRequestedEvent(order.getId(), order.getTotalAmount()));
        } else {
            // Compensate: cancel
            order.transitionTo(OrderStatus.CANCELLED);
            orderRepo.save(order);
            eventPublisher.publish(new OrderCancelledEvent(order.getId(), "INSUFFICIENT_STOCK"));
        }
    }

    @KafkaListener(topics = "payment.result")
    public void onPaymentResult(PaymentResultEvent event) {
        Order order = orderRepo.findById(event.getOrderId()).orElseThrow();

        if (event.isSuccess()) {
            order.transitionTo(OrderStatus.PAID);
            orderRepo.save(order);
            eventPublisher.publish(new OrderCompletedEvent(order.getId()));
        } else {
            // Compensate: release inventory, cancel order
            order.transitionTo(OrderStatus.PAYMENT_FAILED);
            orderRepo.save(order);
            eventPublisher.publish(new InventoryReleaseRequestedEvent(order.getId(), order.getItems()));
            eventPublisher.publish(new OrderCancelledEvent(order.getId(), "PAYMENT_FAILED"));
        }
    }
}
```
Layer 4: Idempotency — Prevent Duplicate Processing
Problem: Kafka at-least-once → consumer có thể nhận cùng 1 event 2 lần → charge payment 2 lần = disaster.

```
@Service
public class IdempotencyService {

    private final RedisTemplate<String, String> redis;

    // Key = "idempotency:{eventType}:{eventId}"
    // Value = processed result
    // TTL = 24h

    public boolean isDuplicate(String eventType, String eventId) {
        String key = "idempotency:%s:%s".formatted(eventType, eventId);
        // SETNX — Set if Not Exists, atomic operation
        Boolean wasAbsent = redis.opsForValue().setIfAbsent(key, "processed", Duration.ofHours(24));
        return Boolean.FALSE.equals(wasAbsent); // false = key đã tồn tại = duplicate
    }
}

// Dùng trong Kafka listener
@KafkaListener(topics = "payment.requested")
public void onPaymentRequested(PaymentRequestedEvent event) {
    if (idempotencyService.isDuplicate("payment.requested", event.getEventId())) {
        log.warn("Duplicate event detected, skipping: {}", event.getEventId());
        return;
    }
    // process payment...
}
```

Layer 5: Database Schema — Production-grade

```
-- Orders table với audit trail
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    status          VARCHAR(30) NOT NULL,
    total_amount    NUMERIC(15, 2) NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,  -- optimistic lock
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit log — append only, không update/delete
CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders(id),
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status) WHERE status NOT IN ('COMPLETED', 'CANCELLED');
-- Partial index — chỉ index active orders, giảm index size ~60%

CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
-- Dùng cho query "orders in last 30 days"

-- Inventory
CREATE TABLE inventory (
    product_id  UUID PRIMARY KEY,
    quantity    INT NOT NULL CHECK (quantity >= 0),
    version     BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```