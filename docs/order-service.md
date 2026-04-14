![alt text](image-1.png)

![alt text](image-2.png)


4. Code — Runnable, Layered, Production-grade
Model Layer — Order Business Model
java// Model layer example (business model + state transitions)
// Order.java
public class Order {
    private final UUID id;
    private final UUID userId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private final Instant createdAt;
    private Instant updatedAt;

    // Domain events — collected in model, published by Service layer
    // Model không biết Kafka tồn tại
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Order(UUID id, UUID userId, List<OrderItem> items) {
        this.id = id;
        this.userId = userId;
        this.items = List.copyOf(items);
        this.status = OrderStatus.PENDING;
        this.totalAmount = calculateTotal(items);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Factory method — enforce creation invariants
    public static Order create(UUID userId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("Order must have at least one item");
        }
        UUID id = UUID.randomUUID();
        Order order = new Order(id, userId, items);

        // Raise domain event — Service layer sẽ publish
        order.domainEvents.add(new OrderCreatedEvent(id, userId, items));
        return order;
    }

    // Transition method — single source of truth cho state change
    public void transitionTo(OrderStatus next, String reason) {
        this.status.validateTransition(next); // throws nếu invalid
        OrderStatus previous = this.status;
        this.status = next;
        this.updatedAt = Instant.now();

        // Emit event cho mỗi transition — Service layer handle
        domainEvents.add(new OrderStatusChangedEvent(id, previous, next, reason));
    }

    // Business rule methods — không để caller tự gọi transitionTo
    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED, "Order confirmed after validation");
    }

    public void startPaymentProcessing() {
        transitionTo(OrderStatus.PAYMENT_PROCESSING, "Payment initiated");
    }

    public void markPaid() {
        transitionTo(OrderStatus.PAID, "Payment successful");
    }

    public void markPaymentFailed(String failureReason) {
        transitionTo(OrderStatus.PAYMENT_FAILED, failureReason);
    }

    public void complete() {
        transitionTo(OrderStatus.COMPLETED, "Order fulfilled and delivered");
    }

    public void cancel(String reason) {
        transitionTo(OrderStatus.CANCELLED, reason);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear(); // consume once
        return events;
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

OrderStatus — Enforce Transitions
javapublic enum OrderStatus {
    PENDING,
    CONFIRMED,
    PAYMENT_PROCESSING,
    PAID,
    PAYMENT_FAILED,
    FULFILLING,
    COMPLETED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
        PENDING,            Set.of(CONFIRMED, CANCELLED),
        CONFIRMED,          Set.of(PAYMENT_PROCESSING, CANCELLED),
        PAYMENT_PROCESSING, Set.of(PAID, PAYMENT_FAILED),
        PAID,               Set.of(FULFILLING),
        PAYMENT_FAILED,     Set.of(CANCELLED),
        FULFILLING,         Set.of(COMPLETED),
        COMPLETED,          Set.of(),   // terminal
        CANCELLED,          Set.of()    // terminal
    );

    public void validateTransition(OrderStatus next) {
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(this, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateTransitionException(
                "Invalid transition: %s → %s. Allowed: %s".formatted(this, next, allowed)
            );
        }
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}

Service Layer — Use Case Orchestration
java// OrderService.java
// Đây là transaction boundary — @Transactional ở đây, KHÔNG đặt trong model
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository; // Outbox Pattern
    private final OrderItemValidator itemValidator;

    // Use case: Create Order
    @Transactional
    public UUID createOrder(CreateOrderCommand command) {
        // 1. Validate input items (price, existence)
        itemValidator.validate(command.items());

        // 2. Build domain object
        List<OrderItem> items = command.items().stream()
            .map(dto -> new OrderItem(dto.productId(), dto.quantity(), dto.unitPrice()))
            .toList();

        Order order = Order.create(command.userId(), items);
        order.confirm(); // immediately confirm nếu validation pass

        // 3. Persist order
        orderRepository.save(order);

        // 4. Persist domain events vào Outbox — cùng transaction
        //    Outbox worker sẽ publish sang Kafka async, đảm bảo at-least-once
        List<DomainEvent> events = order.pullDomainEvents();
        outboxRepository.saveAll(toOutboxMessages(events));

        // KHÔNG publish Kafka trực tiếp ở đây
        // Lý do: nếu Kafka publish fail sau khi DB commit → event lost
        // Outbox pattern giải quyết vấn đề này

        return order.getId();
    }

    // Use case: Handle payment result (called by Kafka consumer)
    @Transactional
    public void handlePaymentResult(PaymentResultCommand command) {
        Order order = orderRepository.findById(command.orderId())
            .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        // Guard: idempotency — nếu đã xử lý rồi thì skip
        if (order.getStatus().isTerminal()) {
            log.warn("Order {} already in terminal state {}, ignoring payment result",
                command.orderId(), order.getStatus());
            return;
        }

        if (command.success()) {
            order.markPaid();
        } else {
            order.markPaymentFailed(command.failureReason());
            order.cancel("Payment failed: " + command.failureReason());
        }

        orderRepository.save(order);
        outboxRepository.saveAll(toOutboxMessages(order.pullDomainEvents()));
    }

    private List<OutboxMessage> toOutboxMessages(List<DomainEvent> events) {
        return events.stream()
            .map(e -> new OutboxMessage(
                e.getClass().getSimpleName(),
                e.getAggregateId().toString(),
                serialize(e)
            ))
            .toList();
    }
}
```

---

### Outbox Pattern — Tại sao cần, tại sao đây là trap phổ biến

**Problem cụ thể:**
```
Transaction commit → DB saved ✅
→ Kafka publish → FAIL ❌
→ Inventory Service không nhận được event
→ Order bị treo ở CONFIRMED mãi mãi
```

**Outbox Pattern giải quyết:**
```
Transaction = [save Order] + [save OutboxMessage] — atomic
Outbox Worker (separate thread) → poll OutboxMessage → publish Kafka → mark published
java// OutboxMessage.java — Entity
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {
    @Id
    private UUID id = UUID.randomUUID();

    private String eventType;       // "OrderCreatedEvent"
    private String aggregateId;     // orderId
    private String payload;         // JSON
    private boolean published = false;
    private Instant createdAt = Instant.now();
    private Instant publishedAt;
}
sql-- Schema
CREATE TABLE outbox_messages (
    id            UUID PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    aggregate_id  VARCHAR(100) NOT NULL,
    payload       JSONB NOT NULL,
    published     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ
);

-- Index để Outbox Worker poll nhanh
CREATE INDEX idx_outbox_unpublished
    ON outbox_messages(created_at)
    WHERE published = FALSE;
java// OutboxWorker.java — scheduled poller
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 500) // poll mỗi 500ms
    @Transactional
    public void publishPendingMessages() {
        List<OutboxMessage> pending = outboxRepo.findUnpublished(Limit.of(100));

        for (OutboxMessage msg : pending) {
            try {
                kafkaTemplate.send(
                    topicFor(msg.getEventType()),
                    msg.getAggregateId(),
                    msg.getPayload()
                ).get(5, TimeUnit.SECONDS); // sync send với timeout

                msg.markPublished();
                outboxRepo.save(msg);

            } catch (Exception e) {
                log.error("Failed to publish outbox message {}: {}", msg.getId(), e.getMessage());
                // Không throw — để retry vào lần poll tiếp theo
            }
        }
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "OrderCreatedEvent"       -> "orders.created";
            case "OrderStatusChangedEvent" -> "orders.status-changed";
            case "OrderCancelledEvent"     -> "orders.cancelled";
            default -> throw new UnknownEventTypeException(eventType);
        };
    }
}

Repository/Integration Layer — JPA Entity và persistence wiring
java// OrderJpaEntity.java — Persistence concern, tách khỏi Service logic
// Business model không cần phụ thuộc @Entity
@Entity
@Table(name = "orders")
public class OrderJpaEntity {
    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal totalAmount;

    @Version
    private Long version; // optimistic locking

    private Instant createdAt;
    private Instant updatedAt;

    // Mapping methods
    public static OrderJpaEntity fromDomain(Order order) { ... }
    public Order toDomain() { ... }
}
java// OrderRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpa;

    @Override
    public void save(Order order) {
        OrderJpaEntity entity = OrderJpaEntity.fromDomain(order);
        jpa.save(entity);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id).map(OrderJpaEntity::toDomain);
    }
}

5. 3 Phương án Design
Option A: Anemic Domain Model (Anti-pattern — nhưng phổ biến)
java// Service làm hết logic, Order chỉ là data bag
orderService.validateAndConfirm(order);
orderService.processPayment(order);

Advantages: Code nhanh, quen thuộc với fresher
Disadvantages: Business logic nằm rải rác trong service, không testable, không enforce invariants
When to use: Prototype nhanh, team chưa biết DDD
When to avoid: Production, khi cần audit trail, khi có nhiều business rules

Option B: Rich Domain Model (DDD Aggregate) — dùng trong code trên

Advantages: Business rules tập trung trong domain, testable không cần Spring context, rõ ràng khi interview
Disadvantages: Learning curve cao hơn, cần tách JPA entity riêng
When to use: Khi business logic phức tạp, khi cần demonstrate system design depth
When to avoid: Simple CRUD không có business rules phức tạp

Option C: Event Sourcing — store events thay vì state
java// Không store current state, store sequence of events
// Rebuild state bằng cách replay events
orderEventStore.append(new OrderCreatedEvent(...));
orderEventStore.append(new OrderConfirmedEvent(...));
Order order = orderEventStore.rebuild(orderId); // replay all events

Advantages: Perfect audit trail, time-travel debugging, không mất history
Disadvantages: Complexity cao, query khó (cần CQRS), overkill cho hầu hết cases
When to use: Financial systems, audit-critical domains
When to avoid: Project 3 tháng với team fresher — over-engineering


6. Common Mistakes / Traps
1. @Transactional trên Domain method
java// WRONG — Domain không biết transaction
@Transactional
public void confirm() { ... }

// RIGHT — Transaction boundary ở Service layer
@Transactional
public UUID createOrder(CreateOrderCommand cmd) { ... }
2. Publish Kafka trong transaction
java// WRONG — Kafka publish không tham gia DB transaction
@Transactional
public void createOrder(...) {
    orderRepo.save(order);
    kafkaTemplate.send(...); // nếu fail ở đây → event lost, order đã saved
}

// RIGHT — Outbox Pattern, publish sau transaction
3. Không có idempotency check khi nhận event
java// Kafka at-least-once → consumer nhận 2 lần
// WRONG: không check → charge payment 2 lần
// RIGHT: check order status trước khi process
if (order.getStatus().isTerminal()) return;
4. Expose entity/model object trực tiếp qua API
java// WRONG
@GetMapping("/{id}")
public Order getOrder(@PathVariable UUID id) { ... } // expose internal state

// RIGHT — DTO
@GetMapping("/{id}")
public OrderResponse getOrder(@PathVariable UUID id) { ... }
```

**5. Không có status_history table**
```
Không có audit trail → khi production incident xảy ra, không biết order đã đi qua những state nào, lúc nào, tại sao

7. Optimal Advice cho Bảo-san
Thứ tự implement đúng:

Viết OrderStatus với transition map trước — test hết valid/invalid transitions
Viết Order model object — test business rules trước
Viết OrderService — mock repository, test use cases
Viết JPA entity và repository — integration test với PostgreSQL thật
Viết Outbox Worker cuối cùng

Điều quan trọng mà Bảo-san chưa hỏi: Correlation ID. Trong distributed system, mỗi request phải có 1 ID xuyên suốt — từ API Gateway → Order Service → Kafka event → Inventory Service → log. Không có correlation ID → khi production fail không trace được gì.
java// MDC — đặt vào Filter, tự động inject vào mọi log line
MDC.put("correlationId", request.getHeader("X-Correlation-ID"));
// Kafka producer: gửi correlationId trong event header
// Kafka consumer: extract correlationId từ header, set vào MDC ngay đầu listener