# DB Schema — E-commerce Order Processing System
---

## Table of Contents

1. [Overview](#1-overview)
2. [Design Principles](#2-design-principles)
3. [Order Service — `order_db`](#3-order-service--order_db)
   - [orders](#31-orders)
   - [order_items](#32-order_items)
   - [order_status_history](#33-order_status_history)
   - [outbox_messages](#34-outbox_messages)
    - [product_catalog](#35-product_catalog)
    - [carts](#36-carts)
    - [cart_items](#37-cart_items)
    - [checkout_sessions](#38-checkout_sessions)
    - [order_read_model](#39-order_read_model)
4. [Inventory Service — `inventory_db`](#4-inventory-service--inventory_db)
   - [inventory](#41-inventory)
   - [inventory_reservations](#42-inventory_reservations)
5. [Payment Service — `payment_db`](#5-payment-service--payment_db)
   - [payments](#51-payments)
6. [Notification Service — `notification_db`](#6-notification-service--notification_db)
   - [notifications](#61-notifications)
7. [Index Strategy](#7-index-strategy)
8. [Constraints & Invariants](#8-constraints--invariants)
9. [Migration Strategy](#9-migration-strategy)
10. [ERD Summary](#10-erd-summary)

---

## 1. Overview

Each service owns exactly one PostgreSQL database. No cross-database joins. No shared tables.

| Service | Database | Tables |
|---|---|---|
| Order Service | `order_db` | `product_catalog`, `carts`, `cart_items`, `checkout_sessions`, `orders`, `order_items`, `order_status_history`, `outbox_messages`, `order_read_model` |
| Inventory Service | `inventory_db` | `inventory`, `inventory_reservations` |
| Payment Service | `payment_db` | `payments` |
| Notification Service | `notification_db` | `notifications` |

**PostgreSQL version:** 15+

**Shared conventions across all databases:**
- All primary keys: `UUID` generated via `gen_random_uuid()`
- All timestamps: `TIMESTAMPTZ` (UTC — never `TIMESTAMP WITHOUT TIME ZONE`)
- All monetary values: `NUMERIC(15,2)` — never `FLOAT` or `DOUBLE PRECISION`
- Soft delete: NOT used — hard delete or status-based archiving only
- All tables have `created_at` — most have `updated_at`

---

## 2. Design Principles

### 2.1 No Shared Database

Each service connects only to its own database. Cross-service data access happens exclusively via Kafka events or API calls — never via direct DB query.

```
order-service     → order_db       ✅
order-service     → inventory_db   ❌ PROHIBITED
inventory-service → order_db       ❌ PROHIBITED
```

### 2.2 Immutable Audit Tables

`order_status_history` and `inventory_reservations` are append-only. Application code MUST NOT issue `UPDATE` or `DELETE` on these tables.

### 2.3 DB as Last Line of Defense

`CHECK` constraints and `NOT NULL` enforce business invariants at DB level, independent of application logic. If application code has a bug, the DB rejects the write.

### 2.4 Optimistic Locking via `version`

Tables that require concurrent write protection carry a `version BIGINT` column. JPA `@Version` increments this on every `UPDATE`. If two transactions read the same version and both attempt to write, one gets `affected rows = 0` → `OptimisticLockException`.

### 2.5 Partial Indexes over Full Indexes

For high-cardinality status columns where queries access only a subset of rows, partial indexes are preferred — smaller, faster, better cache hit rate.

```sql
-- Partial: indexes only active orders (~5% of rows in steady state)
CREATE INDEX idx_orders_status_active
    ON orders(status)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED');
```

---

## 3. Order Service — `order_db`

### 3.1 `orders`

Central table. One row per order. Owned exclusively by Order Service.

```sql
CREATE TABLE orders (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    status          VARCHAR(30)     NOT NULL,
    total_amount    NUMERIC(15,2)   NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_orders_total_amount CHECK (total_amount > 0),
    CONSTRAINT chk_orders_status CHECK (status IN (
        'PENDING',
        'CONFIRMED',
        'PAYMENT_PROCESSING',
        'PAID',
        'PAYMENT_FAILED',
        'FULFILLING',
        'COMPLETED',
        'CANCELLED'
    ))
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | Primary key |
| `user_id` | UUID | NO | — | References customer — no FK (cross-service) |
| `status` | VARCHAR(30) | NO | — | Current state machine position |
| `total_amount` | NUMERIC(15,2) | NO | — | Sum of all `order_items.unit_price * quantity` |
| `version` | BIGINT | NO | 0 | Optimistic locking counter — incremented on every UPDATE |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | Order creation timestamp |
| `updated_at` | TIMESTAMPTZ | NO | `NOW()` | Last state transition timestamp |

**Valid status transitions:**

```
PENDING → CONFIRMED → PAYMENT_PROCESSING → PAID → FULFILLING → COMPLETED
                  ↘              ↘
               CANCELLED      PAYMENT_FAILED → CANCELLED
```

**Indexes:**

```sql
CREATE INDEX idx_orders_user_id
    ON orders(user_id);

CREATE INDEX idx_orders_status_active
    ON orders(status)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED');

CREATE INDEX idx_orders_created_at
    ON orders(created_at DESC);

CREATE INDEX idx_orders_user_created
    ON orders(user_id, created_at DESC);
```

**Notes:**
- `user_id` has no FK — customer service is external. Integrity enforced at application layer.
- `version` is managed entirely by JPA `@Version` — never manually set by application code.
- `updated_at` must be refreshed on every state transition via `@PreUpdate`.

---

### 3.2 `order_items`

Line items belonging to an order. Immutable after creation — never updated.

```sql
CREATE TABLE order_items (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID            NOT NULL,
    quantity    INT             NOT NULL,
    unit_price  NUMERIC(15,2)   NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_order_items_quantity   CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price > 0)
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | Primary key |
| `order_id` | UUID | NO | — | FK to `orders.id` |
| `product_id` | UUID | NO | — | References product catalog (external — no FK) |
| `quantity` | INT | NO | — | Number of units ordered |
| `unit_price` | NUMERIC(15,2) | NO | — | Price per unit at time of order — snapshot, not live price |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | Item creation timestamp |

**Indexes:**

```sql
CREATE INDEX idx_order_items_order_id   ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
```

**Notes:**
- `unit_price` is a price snapshot at order time. Does NOT update if product price changes later — intentional for financial correctness.
- `ON DELETE CASCADE` on `order_id` — if an order is hard-deleted, items are removed automatically.

---

### 3.3 `order_status_history`

Append-only audit log. One row per state transition. Never updated, never deleted.

```sql
CREATE TABLE order_status_history (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders(id),
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_history_to_status CHECK (to_status IN (
        'PENDING',
        'CONFIRMED',
        'PAYMENT_PROCESSING',
        'PAID',
        'PAYMENT_FAILED',
        'FULFILLING',
        'COMPLETED',
        'CANCELLED'
    ))
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | Primary key |
| `order_id` | UUID | NO | — | FK to `orders.id` |
| `from_status` | VARCHAR(30) | YES | — | Previous status. NULL for initial PENDING entry |
| `to_status` | VARCHAR(30) | NO | — | New status after transition |
| `reason` | TEXT | YES | — | Human-readable reason for transition |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | Exact timestamp of transition |

**Indexes:**

```sql
CREATE INDEX idx_history_order_id ON order_status_history(order_id);

CREATE INDEX idx_history_to_status_created
    ON order_status_history(to_status, created_at DESC)
    WHERE to_status IN ('CANCELLED', 'PAYMENT_FAILED');
```

**Sample data:**

```
order_id | from_status        | to_status          | reason                       | created_at
---------+--------------------+--------------------+------------------------------+---------------------------
ord-001  | NULL               | PENDING            | Order created                | 2026-03-17 10:00:00+00
ord-001  | PENDING            | CONFIRMED          | Order confirmed              | 2026-03-17 10:00:00+00
ord-001  | CONFIRMED          | PAYMENT_PROCESSING | Payment initiated            | 2026-03-17 10:00:01+00
ord-001  | PAYMENT_PROCESSING | PAID               | Payment successful           | 2026-03-17 10:00:03+00
ord-001  | PAID               | FULFILLING         | Fulfillment started          | 2026-03-17 10:00:03+00
ord-001  | FULFILLING         | COMPLETED          | Order fulfilled              | 2026-03-17 10:00:04+00
```

**Notes:**
- `from_status` is NULL only for the first row (PENDING creation).
- Application must write to this table on every call to `Order.transitionTo()`.
- No `UPDATE` or `DELETE` ever issued — enforce via code review gate.

---

### 3.4 `outbox_messages`

Transactional outbox for guaranteed Kafka event delivery. OutboxWorker polls this table and publishes unpublished messages to Kafka.

```sql
CREATE TABLE outbox_messages (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(100)    NOT NULL,
    topic           VARCHAR(200)    NOT NULL,
    payload         JSONB           NOT NULL,
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INT             NOT NULL DEFAULT 0,
    last_error      TEXT
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | Primary key |
| `event_type` | VARCHAR(100) | NO | — | Java class name e.g. `OrderCreatedEvent` |
| `aggregate_id` | VARCHAR(100) | NO | — | `orderId` — used as Kafka message key for partition ordering |
| `topic` | VARCHAR(200) | NO | — | Target Kafka topic e.g. `orders.created` |
| `payload` | JSONB | NO | — | Full serialized event body |
| `published` | BOOLEAN | NO | FALSE | Whether OutboxWorker successfully published to Kafka |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | When event was registered |
| `published_at` | TIMESTAMPTZ | YES | — | When OutboxWorker successfully published |
| `retry_count` | INT | NO | 0 | Number of failed publish attempts |
| `last_error` | TEXT | YES | — | Last publish error message for debugging |

**Indexes:**

```sql
-- OutboxWorker primary poll query — must be fast
CREATE INDEX idx_outbox_unpublished
    ON outbox_messages(created_at ASC)
    WHERE published = FALSE;

-- OutboxWorker poll query (multi-instance safe):
-- SELECT * FROM outbox_messages
-- WHERE published = FALSE
-- ORDER BY created_at ASC
-- LIMIT 100
-- FOR UPDATE SKIP LOCKED;
```

**Retention:**

```sql
-- Run daily — purge published messages older than 7 days
DELETE FROM outbox_messages
WHERE published = TRUE
  AND published_at < NOW() - INTERVAL '7 days';
```

**Notes:**
- `aggregate_id` as Kafka key ensures all events for the same order go to the same partition, preserving order.
- `FOR UPDATE SKIP LOCKED` in poll query prevents duplicate processing across multiple OutboxWorker instances.
- `last_error` and `retry_count` are for operational visibility — OutboxWorker retries indefinitely. Manual inspection via `last_error` if needed.

---

### 3.5 `product_catalog`

Product catalog table for demo listing and product detail APIs in existing Order Service.

```sql
CREATE TABLE product_catalog (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             VARCHAR(64)     NOT NULL UNIQUE,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    price           NUMERIC(15,2)   NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_product_catalog_price CHECK (price > 0)
);
```

**Indexes:**

```sql
CREATE INDEX idx_product_catalog_active
    ON product_catalog(is_active)
    WHERE is_active = TRUE;

CREATE INDEX idx_product_catalog_name
    ON product_catalog(name);
```

---

### 3.6 `carts`

Shopping cart header table (one active cart per user).

```sql
CREATE TABLE carts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_carts_status CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED'))
);
```

**Indexes:**

```sql
CREATE INDEX idx_carts_user_active
    ON carts(user_id)
    WHERE status = 'ACTIVE';
```

---

### 3.7 `cart_items`

Line items within cart. Enforces one row per product per cart.

```sql
CREATE TABLE cart_items (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id         UUID            NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL REFERENCES product_catalog(id),
    quantity        INT             NOT NULL,
    unit_price      NUMERIC(15,2)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_cart_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);
```

**Indexes:**

```sql
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);
```

---

### 3.8 `checkout_sessions`

Tracks checkout lifecycle and links cart conversion to resulting order.

```sql
CREATE TABLE checkout_sessions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID            NOT NULL REFERENCES carts(id),
    user_id             UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    subtotal_amount     NUMERIC(15,2)   NOT NULL,
    final_amount        NUMERIC(15,2)   NOT NULL,
    order_id            UUID,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT chk_checkout_status CHECK (status IN ('CREATED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_checkout_subtotal CHECK (subtotal_amount >= 0),
    CONSTRAINT chk_checkout_final CHECK (final_amount >= 0)
);
```

**Indexes:**

```sql
CREATE INDEX idx_checkout_sessions_user_created
    ON checkout_sessions(user_id, created_at DESC);

CREATE INDEX idx_checkout_sessions_status_created
    ON checkout_sessions(status, created_at DESC);
```

---

### 3.9 `order_read_model`

Denormalized query projection for list/timeline APIs (customer and admin).

```sql
CREATE TABLE order_read_model (
    order_id             UUID            PRIMARY KEY,
    user_id              UUID            NOT NULL,
    status               VARCHAR(30)     NOT NULL,
    payment_status       VARCHAR(30),
    total_amount         NUMERIC(15,2)   NOT NULL,
    item_count           INT             NOT NULL,
    last_event_at        TIMESTAMPTZ     NOT NULL,
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_read_model_total_amount CHECK (total_amount > 0),
    CONSTRAINT chk_read_model_item_count CHECK (item_count > 0)
);
```

**Indexes:**

```sql
CREATE INDEX idx_order_read_model_user_updated
    ON order_read_model(user_id, updated_at DESC);

CREATE INDEX idx_order_read_model_status_updated
    ON order_read_model(status, updated_at DESC);
```

---

## 4. Inventory Service — `inventory_db`

### 4.1 `inventory`

One row per product. Current stock level with optimistic locking.

```sql
CREATE TABLE inventory (
    product_id  UUID        PRIMARY KEY,
    quantity    INT         NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_inventory_quantity CHECK (quantity >= 0)
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `product_id` | UUID | NO | — | PK — references product catalog (external, no FK) |
| `quantity` | INT | NO | — | Current available stock units |
| `version` | BIGINT | NO | 0 | Optimistic locking counter |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | When stock record was first created |
| `updated_at` | TIMESTAMPTZ | NO | `NOW()` | Last reservation or restock timestamp |

**Indexes:**

```sql
-- PK on product_id covers all point lookups — no additional indexes needed

-- Low-stock monitoring
CREATE INDEX idx_inventory_low_stock
    ON inventory(quantity)
    WHERE quantity < 10;
```

**Concurrency behavior:**

```sql
-- JPA @Version generates this on every save:
UPDATE inventory
SET quantity   = :newQty,
    version    = :currentVersion + 1,
    updated_at = NOW()
WHERE product_id = :productId
  AND version    = :currentVersion;

-- affected rows = 0 → OptimisticLockException → application retries (max 3, 50ms backoff)
-- CHECK (quantity >= 0) → rejects negative stock even if retry logic has a bug
```

**Notes:**
- `CHECK (quantity >= 0)` is the critical safety net — prevents oversell even when application-level optimistic locking has edge cases.
- `product_id` as PK — no surrogate key needed since product IDs are already globally unique UUIDs.

---

### 4.2 `inventory_reservations`

Append-only log of all reservation and release operations. Used for saga audit and idempotency verification.

```sql
CREATE TABLE inventory_reservations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID        NOT NULL,
    product_id      UUID        NOT NULL REFERENCES inventory(product_id),
    quantity        INT         NOT NULL,
    operation       VARCHAR(10) NOT NULL,
    status          VARCHAR(10) NOT NULL,
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reservation_quantity  CHECK (quantity > 0),
    CONSTRAINT chk_reservation_operation CHECK (operation IN ('RESERVE', 'RELEASE')),
    CONSTRAINT chk_reservation_status    CHECK (status IN ('SUCCESS', 'FAILED'))
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | Primary key |
| `order_id` | UUID | NO | — | The order that triggered this operation |
| `product_id` | UUID | NO | — | FK to `inventory.product_id` |
| `quantity` | INT | NO | — | Units reserved or released |
| `operation` | VARCHAR(10) | NO | — | `RESERVE` or `RELEASE` |
| `status` | VARCHAR(10) | NO | — | `SUCCESS` or `FAILED` |
| `failure_reason` | TEXT | YES | — | Why reservation failed e.g. `INSUFFICIENT_STOCK` |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | When operation was attempted |

**Indexes:**

```sql
CREATE INDEX idx_reservations_order_id   ON inventory_reservations(order_id);
CREATE INDEX idx_reservations_product_id ON inventory_reservations(product_id);

CREATE INDEX idx_reservations_failed
    ON inventory_reservations(created_at DESC)
    WHERE status = 'FAILED';
```

**Sample data:**

```
order_id | product_id | qty | operation | status  | failure_reason     | created_at
---------+------------+-----+-----------+---------+--------------------+------------------
ord-001  | prod-A     | 2   | RESERVE   | SUCCESS | NULL               | 2026-03-17 10:00
ord-002  | prod-A     | 5   | RESERVE   | FAILED  | INSUFFICIENT_STOCK | 2026-03-17 10:01
ord-001  | prod-A     | 2   | RELEASE   | SUCCESS | NULL               | 2026-03-17 10:05
```

---

## 5. Payment Service — `payment_db`

### 5.1 `payments`

One row per order. Tracks charge and refund lifecycle.

```sql
CREATE TABLE payments (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID            NOT NULL UNIQUE,
    user_id                 UUID            NOT NULL,
    amount                  NUMERIC(15,2)   NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    gateway_transaction_id  VARCHAR(255),
    failure_reason          TEXT,
    refund_reason           TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_status CHECK (status IN (
        'PENDING',
        'CHARGED',
        'FAILED',
        'REFUND_REQUESTED',
        'REFUNDED',
        'REFUND_FAILED'
    ))
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | Primary key |
| `order_id` | UUID | NO | — | `UNIQUE` — one payment record per order |
| `user_id` | UUID | NO | — | Customer who owns the order |
| `amount` | NUMERIC(15,2) | NO | — | Amount charged or to be refunded |
| `status` | VARCHAR(20) | NO | — | Current payment lifecycle state |
| `gateway_transaction_id` | VARCHAR(255) | YES | — | External gateway reference — set on CHARGED |
| `failure_reason` | TEXT | YES | — | Gateway error message on FAILED |
| `refund_reason` | TEXT | YES | — | Why refund was requested |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | Record creation |
| `updated_at` | TIMESTAMPTZ | NO | `NOW()` | Last status update |

**Valid status transitions:**

```
PENDING → CHARGED → REFUND_REQUESTED → REFUNDED
       ↘                             ↘ REFUND_FAILED  ← manual intervention required
        FAILED
```

**Indexes:**

```sql
-- UNIQUE on order_id already creates an implicit index — covers GET /payments/{orderId}

CREATE INDEX idx_payments_user_id ON payments(user_id);

CREATE INDEX idx_payments_status_failed
    ON payments(created_at DESC)
    WHERE status IN ('FAILED', 'REFUND_FAILED');
```

**Notes:**
- `UNIQUE` on `order_id` enforces one payment record per order at DB level — critical idempotency backstop.
- `gateway_transaction_id` is required for refund — must be persisted when transitioning to `CHARGED`.
- Never store raw card data or CVV — gateway handles tokenization entirely.
- `NUMERIC(15,2)` supports amounts up to 9,999,999,999,999.99 — sufficient for VND at scale.

---

## 6. Notification Service — `notification_db`

### 6.1 `notifications`

One row per notification attempt per event type. Multiple rows per order possible.

```sql
CREATE TABLE notifications (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    type            VARCHAR(50)     NOT NULL,
    recipient_email VARCHAR(255)    NOT NULL,
    subject         VARCHAR(500),
    status          VARCHAR(20)     NOT NULL,
    retry_count     INT             NOT NULL DEFAULT 0,
    last_error      TEXT,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_notifications_type CHECK (type IN (
        'ORDER_CONFIRMED',
        'ORDER_COMPLETED',
        'ORDER_CANCELLED',
        'PAYMENT_FAILED'
    )),
    CONSTRAINT chk_notifications_status CHECK (status IN (
        'PENDING',
        'SENT',
        'FAILED'
    )),
    CONSTRAINT chk_notifications_retry   CHECK (retry_count >= 0),
    CONSTRAINT uq_notifications_order_type UNIQUE (order_id, type)
);
```

**Column reference:**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | Primary key |
| `order_id` | UUID | NO | — | Associated order |
| `user_id` | UUID | NO | — | Recipient customer |
| `type` | VARCHAR(50) | NO | — | Notification type / template selector |
| `recipient_email` | VARCHAR(255) | NO | — | Delivery address — snapshot at send time |
| `subject` | VARCHAR(500) | YES | — | Email subject line |
| `status` | VARCHAR(20) | NO | — | Current delivery status |
| `retry_count` | INT | NO | 0 | Number of failed send attempts |
| `last_error` | TEXT | YES | — | SMTP/provider error on last failed attempt |
| `sent_at` | TIMESTAMPTZ | YES | — | Timestamp of successful delivery |
| `created_at` | TIMESTAMPTZ | NO | `NOW()` | When notification was queued |
| `updated_at` | TIMESTAMPTZ | NO | `NOW()` | Last status update |

**Indexes:**

```sql
CREATE INDEX idx_notifications_order_id ON notifications(order_id);

CREATE INDEX idx_notifications_pending
    ON notifications(created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_notifications_failed
    ON notifications(updated_at DESC)
    WHERE status = 'FAILED';
```

**Idempotency via UNIQUE constraint:**

`UNIQUE (order_id, type)` ensures one notification per event type per order at DB level.

On duplicate Kafka event → `INSERT` violates constraint → application catches `DataIntegrityViolationException` → skips silently → idempotent. No Redis needed for this service.

**Retention:**

```sql
DELETE FROM notifications
WHERE created_at < NOW() - INTERVAL '90 days';
```

---

## 7. Index Strategy

### Summary Table

| Table | Index | Type | Columns | Condition | Purpose |
|---|---|---|---|---|---|
| `orders` | `idx_orders_user_id` | BTREE | `user_id` | — | Orders by customer |
| `orders` | `idx_orders_status_active` | BTREE partial | `status` | `NOT IN ('COMPLETED','CANCELLED')` | Active order monitoring |
| `orders` | `idx_orders_created_at` | BTREE | `created_at DESC` | — | Time-range queries |
| `orders` | `idx_orders_user_created` | BTREE compound | `user_id, created_at DESC` | — | Customer order list sorted |
| `order_items` | `idx_order_items_order_id` | BTREE | `order_id` | — | Items for order |
| `order_items` | `idx_order_items_product_id` | BTREE | `product_id` | — | Orders containing product |
| `order_status_history` | `idx_history_order_id` | BTREE | `order_id` | — | Full history for order |
| `order_status_history` | `idx_history_to_status_created` | BTREE partial | `to_status, created_at DESC` | `IN ('CANCELLED','PAYMENT_FAILED')` | Failure monitoring |
| `outbox_messages` | `idx_outbox_unpublished` | BTREE partial | `created_at ASC` | `published = FALSE` | OutboxWorker poll |
| `inventory` | `idx_inventory_low_stock` | BTREE partial | `quantity` | `quantity < 10` | Low-stock alerts |
| `inventory_reservations` | `idx_reservations_order_id` | BTREE | `order_id` | — | Saga audit |
| `inventory_reservations` | `idx_reservations_product_id` | BTREE | `product_id` | — | Inventory audit |
| `inventory_reservations` | `idx_reservations_failed` | BTREE partial | `created_at DESC` | `status = 'FAILED'` | Failure monitoring |
| `payments` | `idx_payments_user_id` | BTREE | `user_id` | — | Customer payment history |
| `payments` | `idx_payments_status_failed` | BTREE partial | `created_at DESC` | `IN ('FAILED','REFUND_FAILED')` | Payment failure alerts |
| `notifications` | `idx_notifications_order_id` | BTREE | `order_id` | — | Notifications for order |
| `notifications` | `idx_notifications_pending` | BTREE partial | `created_at ASC` | `status = 'PENDING'` | Retry scheduler |
| `notifications` | `idx_notifications_failed` | BTREE partial | `updated_at DESC` | `status = 'FAILED'` | Failure alerting |

### Index Design Rules

1. **Partial indexes first** — filter by status subset → make it partial. Smaller = faster = better cache hit rate.
2. **Compound index column order follows query shape** — `(user_id, created_at)` supports `WHERE user_id = ? ORDER BY created_at DESC`. Reversed does not.
3. **Never index boolean alone** — `CREATE INDEX ON orders(is_active)` is useless (2 distinct values). Use partial: `WHERE is_active = TRUE`.
4. **Index every FK column that appears in WHERE** — every `order_id`, `product_id` FK gets an index.
5. **Monitor unused indexes** — drop after 30 days of zero scans.

```sql
-- Detect unused indexes
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0
ORDER BY tablename, indexname;
```

---

## 8. Constraints & Invariants

### Business Invariants at DB Level

| Table | Constraint | Rule |
|---|---|---|
| `orders` | `chk_orders_total_amount` | `total_amount > 0` |
| `orders` | `chk_orders_status` | One of 8 valid status values |
| `order_items` | `chk_order_items_quantity` | `quantity > 0` |
| `order_items` | `chk_order_items_unit_price` | `unit_price > 0` |
| `inventory` | `chk_inventory_quantity` | `quantity >= 0` — prevents negative stock |
| `inventory_reservations` | `chk_reservation_quantity` | `quantity > 0` |
| `inventory_reservations` | `chk_reservation_operation` | `RESERVE` or `RELEASE` only |
| `inventory_reservations` | `chk_reservation_status` | `SUCCESS` or `FAILED` only |
| `payments` | `chk_payments_amount` | `amount > 0` |
| `payments` | `chk_payments_status` | One of 6 valid status values |
| `payments` | `UNIQUE (order_id)` | One payment record per order |
| `notifications` | `chk_notifications_type` | One of 4 valid types |
| `notifications` | `chk_notifications_status` | `PENDING`, `SENT`, or `FAILED` |
| `notifications` | `UNIQUE (order_id, type)` | One notification per order per type |

### Cascade Rules

| Relationship | ON DELETE | Rationale |
|---|---|---|
| `order_items.order_id → orders.id` | CASCADE | Items have no meaning without parent order |
| `order_status_history.order_id → orders.id` | RESTRICT | Must not delete order if history exists |
| `inventory_reservations.product_id → inventory.product_id` | RESTRICT | Audit records must be preserved |

---

## 9. Migration Strategy

### Tool: Flyway

All schema changes managed via Flyway versioned migrations. No manual DDL on any environment.

**Naming convention:**

```
V{version}__{description}.sql

V1__create_orders_schema.sql
V2__create_inventory_schema.sql
V3__add_outbox_retry_count.sql
V4__add_notifications_unique_constraint.sql
```

**Location per service:**

```
src/main/resources/db/migration/
├── V1__create_orders_schema.sql
├── V2__create_order_items.sql
├── V3__create_order_status_history.sql
└── V4__create_outbox_messages.sql
```

### Migration Rules

1. Never edit an applied migration — create a new version instead
2. No destructive migrations without a rollback plan
3. Additive changes first: add nullable column → deploy → backfill → add NOT NULL
4. Index creation in production uses `CONCURRENTLY` to avoid table lock:

```sql
-- Development
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- Production (non-blocking)
CREATE INDEX CONCURRENTLY idx_orders_user_id ON orders(user_id);
```

### Bootstrap Migration — `order_db`

```sql
-- V1__bootstrap_order_db.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE orders (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    status          VARCHAR(30)     NOT NULL,
    total_amount    NUMERIC(15,2)   NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount > 0),
    CONSTRAINT chk_orders_status CHECK (status IN (
        'PENDING','CONFIRMED','PAYMENT_PROCESSING',
        'PAID','PAYMENT_FAILED','FULFILLING','COMPLETED','CANCELLED'
    ))
);

CREATE TABLE order_items (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID            NOT NULL,
    quantity    INT             NOT NULL,
    unit_price  NUMERIC(15,2)   NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_items_quantity   CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price > 0)
);

CREATE TABLE order_status_history (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders(id),
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_history_to_status CHECK (to_status IN (
        'PENDING','CONFIRMED','PAYMENT_PROCESSING',
        'PAID','PAYMENT_FAILED','FULFILLING','COMPLETED','CANCELLED'
    ))
);

CREATE TABLE outbox_messages (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(100)    NOT NULL,
    topic           VARCHAR(200)    NOT NULL,
    payload         JSONB           NOT NULL,
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INT             NOT NULL DEFAULT 0,
    last_error      TEXT
);

CREATE INDEX idx_orders_user_id             ON orders(user_id);
CREATE INDEX idx_orders_status_active       ON orders(status)
    WHERE status NOT IN ('COMPLETED','CANCELLED');
CREATE INDEX idx_orders_created_at          ON orders(created_at DESC);
CREATE INDEX idx_orders_user_created        ON orders(user_id, created_at DESC);
CREATE INDEX idx_order_items_order_id       ON order_items(order_id);
CREATE INDEX idx_order_items_product_id     ON order_items(product_id);
CREATE INDEX idx_history_order_id           ON order_status_history(order_id);
CREATE INDEX idx_history_to_status_created  ON order_status_history(to_status, created_at DESC)
    WHERE to_status IN ('CANCELLED','PAYMENT_FAILED');
CREATE INDEX idx_outbox_unpublished         ON outbox_messages(created_at ASC)
    WHERE published = FALSE;
```

---

## 10. ERD Summary

### order_db

```
orders (PK: id)
├── id, user_id, status, total_amount, version, created_at, updated_at
│
├──[1:N]── order_items (PK: id, FK: order_id)
│           └── id, order_id, product_id, quantity, unit_price, created_at
│
└──[1:N]── order_status_history (PK: id, FK: order_id)  [APPEND-ONLY]
            └── id, order_id, from_status, to_status, reason, created_at

outbox_messages (PK: id)  [standalone — no FK]
└── id, event_type, aggregate_id, topic, payload, published, created_at,
    published_at, retry_count, last_error
```

### inventory_db

```
inventory (PK: product_id)
├── product_id, quantity, version, created_at, updated_at
│
└──[1:N]── inventory_reservations (PK: id, FK: product_id)  [APPEND-ONLY]
            └── id, order_id, product_id, quantity, operation, status,
                failure_reason, created_at
```

### payment_db

```
payments (PK: id)
└── id, order_id [UNIQUE], user_id, amount, status,
    gateway_transaction_id, failure_reason, refund_reason,
    created_at, updated_at
```

### notification_db

```
notifications (PK: id)
└── id, order_id, user_id, type, recipient_email, subject,
    status, retry_count, last_error, sent_at, created_at, updated_at

UNIQUE (order_id, type)
```

---

*End of document. All schema changes must go through Flyway migration — no manual DDL on any environment.*
