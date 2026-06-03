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

> **Full explanations with trade-offs for cases 1–20 are in** [`ECOMMERCE_EDGE_CASES_V1.md`](./ECOMMERCE_EDGE_CASES_V1.md)
> (or scroll down — both are in this file for completeness).

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

**Code:** [`DeadLetterMessageEntity`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/infrastructure/outbox/DeadLetterMessageEntity.java) | [`V2__additional_edge_cases.sql`](../ecommerce-monolith/src/main/resources/db/migration/V2__additional_edge_cases.sql)

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

**Code:** [`OrderStatusHistoryEntity @Immutable`](../ecommerce-monolith/src/main/java/com/ecommerce/monolith/domain/order/entity/OrderStatusHistoryEntity.java) | [`V2__additional_edge_cases.sql (RLS policies)`](../ecommerce-monolith/src/main/resources/db/migration/V2__additional_edge_cases.sql)

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
