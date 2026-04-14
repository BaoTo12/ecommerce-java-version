# Architecture — E-commerce Order Processing System


## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture Style](#2-architecture-style)
3. [Service Breakdown](#3-service-breakdown)
4. [Inter-Service Communication](#4-inter-service-communication)
5. [Data Architecture](#5-data-architecture)
6. [Saga Pattern — Distributed Transaction](#6-saga-pattern--distributed-transaction)
7. [Outbox Pattern — Event Delivery Guarantee](#7-outbox-pattern--event-delivery-guarantee)
8. [Concurrency Control](#8-concurrency-control)
9. [Idempotency Strategy](#9-idempotency-strategy)
10. [Observability](#10-observability)
11. [Deployment Architecture](#11-deployment-architecture)
12. [Key Architecture Decisions](#12-key-architecture-decisions)

---

## 1. System Overview

### Context

This system processes e-commerce orders end-to-end: from catalog browsing and cart management through checkout, inventory reservation, payment processing, customer notification, and read/query APIs. It is designed to handle concurrent requests without data inconsistency.

### High-Level Flow

```
Client
  │
  │  POST /orders
  ▼
┌─────────────────┐
│  Order Service  │  ← Saga Orchestrator
│  (Orchestrator) │
└────────┬────────┘
         │ Kafka Events
         │
   ┌─────┼──────────────┐
   ▼     ▼              ▼
┌──────┐ ┌─────────┐ ┌──────────────┐
│ INV  │ │ PAYMENT │ │ NOTIFICATION │
│ SVC  │ │   SVC   │ │     SVC      │
└──────┘ └─────────┘ └──────────────┘
   │         │
   ▼         ▼
┌──────┐ ┌─────────┐
│  PG  │ │   PG    │   PostgreSQL (per service)
└──────┘ └─────────┘

Shared Infrastructure: Kafka, Redis
```

### Core Design Principles

| Principle | Implementation |
|---|---|
| Database per service | Each service owns its PostgreSQL schema — no shared DB |
| Async communication | All inter-service calls via Kafka events — no synchronous HTTP between services |
| Eventual consistency | Inter-service state converges via Saga + compensating transactions |
| Event delivery guarantee | Outbox Pattern — events never lost on publish failure |
| Idempotent consumers | Redis-based deduplication on every Kafka consumer |
| Full audit trail | `order_status_history` append-only table — no UPDATE/DELETE |

---


## 3. Service Breakdown

### 3.1 Order Service

**Role:** Saga Orchestrator. Single source of truth for order state.

**State Machine:**

```
PENDING ──────────────────────────────────────────► CANCELLED
   │                                                     ▲
   ▼                                                     │
CONFIRMED ───────────────────────────────────────────────┤
   │                                                     │
   ▼                                                     │
PAYMENT_PROCESSING                                       │
   │                                                     │
   ├──── payment OK ────► PAID ────► FULFILLING ──► COMPLETED
   │
   └──── payment FAIL ──► PAYMENT_FAILED ──────────────► CANCELLED
```

---

### 3.2 Inventory Service

**Optimistic Locking**


**Concurrency Strategy:**

```
SELECT inventory WHERE product_id = ?     ← no lock acquired
  ↓
domain.reserve(quantity)                  ← check in memory
  ↓
JPA save → UPDATE inventory
           SET quantity = ?,
               version  = version + 1
           WHERE product_id = ?
             AND version    = ?           ← optimistic lock check
  ↓
affected rows = 0 → OptimisticLockException → retry (max 3)
```

---

### 3.3 Payment Service

**Payment Status Machine:**

```
(none) ──► CHARGED ──► REFUNDED
              │
              └──► REFUND_FAILED   ← requires manual intervention
(none) ──► FAILED
```

---

### 3.4 Notification Service

EmailSender.java              # Mock SMTP for v1.0

## 4. Inter-Service Communication

### Kafka Topic Registry

All topics follow naming convention: `{domain}.{event}` in kebab-case.

| Topic | Producer | Consumers | Payload |
|---|---|---|---|
| `orders.created` | Order Service | Inventory Service, Notification Service | `OrderCreatedEvent` |
| `orders.status-changed` | Order Service | Notification Service | `OrderStatusChangedEvent` |
| `orders.cancelled` | Order Service | Inventory Service, Notification Service | `OrderCancelledEvent` |
| `inventory.reserved` | Inventory Service | Order Service | `InventoryReservedEvent` |
| `inventory.failed` | Inventory Service | Order Service | `InventoryFailedEvent` |
| `inventory.release-requested` | Order Service | Inventory Service | `InventoryReleaseRequestedEvent` |
| `inventory.released` | Inventory Service | Order Service | `InventoryReleasedEvent` |
| `payment.requested` | Order Service | Payment Service | `PaymentRequestedEvent` |
| `payment.succeeded` | Payment Service | Order Service | `PaymentSucceededEvent` |
| `payment.failed` | Payment Service | Order Service, Notification Service | `PaymentFailedEvent` |
| `payment.refund-requested` | Order Service | Payment Service | `PaymentRefundRequestedEvent` |
| `payment.refunded` | Payment Service | Order Service | `PaymentRefundedEvent` |
| `notifications.send` | — | Notification Service | *(consumed directly from above topics)* |

### Kafka Message Envelope

Every Kafka message MUST carry these headers:

```json
{
  "headers": {
    "correlationId": "uuid-v4",
    "eventType": "OrderCreatedEvent",
    "eventId": "uuid-v4",
    "producedAt": "2026-03-17T10:00:00Z",
    "serviceVersion": "1.0.0"
  },
  "payload": { }
}
```

### Event Schemas

**OrderCreatedEvent**
```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "items": [
    {
      "productId": "uuid",
      "quantity": 2,
      "unitPrice": "150000.00"
    }
  ],
  "totalAmount": "300000.00",
  "createdAt": "2026-03-17T10:00:00Z"
}
```

**InventoryReservedEvent**
```json
{
  "orderId": "uuid",
  "reservedItems": [
    { "productId": "uuid", "quantity": 2 }
  ],
  "reservedAt": "2026-03-17T10:00:01Z"
}
```

**PaymentSucceededEvent**
```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "amount": "300000.00",
  "gatewayTransactionId": "gw-txn-uuid",
  "processedAt": "2026-03-17T10:00:03Z"
}
```

**PaymentFailedEvent**
```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "amount": "300000.00",
  "failureReason": "INSUFFICIENT_FUNDS",
  "failedAt": "2026-03-17T10:00:03Z"
}
```

---

## 5. Data Architecture

### Database per Service

| Service | Database | Schema |
|---|---|---|
| Order Service | `order_db` | `product_catalog`, `carts`, `cart_items`, `checkout_sessions`, `orders`, `order_items`, `order_status_history`, `order_read_model`, `outbox_messages` |
| Inventory Service | `inventory_db` | `inventory` |
| Payment Service | `payment_db` | `payments` |
| Notification Service | `notification_db` | `notifications` |

**Rule:** No service reads another service's database directly. Data sharing happens only via Kafka events.

## 6. Saga Pattern — Distributed Transaction

### Pattern Choice: Orchestration (not Choreography)

Order Service acts as the central orchestrator. It explicitly directs each step and handles failures.

**Why Orchestration over Choreography:**
- Easier to trace the full flow — one place to look (Order Service logs)
- Simpler to reason about compensating transactions
- Better fit for a 3-person team — less distributed complexity to manage
- Choreography adds value at scale (10+ services) — premature for this scope

### Saga Flow — Happy Path

```
Order Service          Inventory Service       Payment Service
     │                        │                      │
     │── OrderCreatedEvent ──►│                      │
     │                        │ reserve stock        │
     │◄── InventoryReserved ──│                      │
     │                        │                      │
     │── PaymentRequested ───────────────────────────►│
     │                        │                 charge│
     │◄── PaymentSucceeded ──────────────────────────│
     │                        │                      │
  COMPLETED                   │                      │
```

### Saga Flow — Failure Path (Payment Fails)

```
Order Service          Inventory Service       Payment Service
     │                        │                      │
     │── OrderCreatedEvent ──►│                      │
     │◄── InventoryReserved ──│                      │
     │── PaymentRequested ───────────────────────────►│
     │◄── PaymentFailed ─────────────────────────────│
     │                        │                      │
     │── InventoryReleaseRequested ──────────────────►│  (compensate)
     │◄── InventoryReleased ──│                      │
     │                        │                      │
  CANCELLED                   │                      │
```

### Saga Flow — Failure Path (Inventory Fails)

```
Order Service          Inventory Service
     │                        │
     │── OrderCreatedEvent ──►│
     │                        │ insufficient stock
     │◄── InventoryFailed ────│
     │
  CANCELLED  (no payment step initiated)
```

### Compensating Transactions

| Failed Step | Compensating Action | Event Published |
|---|---|---|
| Inventory reservation fails | No compensation needed — nothing committed | `OrderCancelledEvent` |
| Payment fails after inventory reserved | Release inventory | `InventoryReleaseRequestedEvent` |
| Refund fails after order cancelled | Flag for manual intervention | `PaymentRefundFailedEvent` |

---

## 7. Outbox Pattern — Event Delivery Guarantee


### Solution

Persist event to `outbox_messages` table **within the same DB transaction** as the state change. A separate `OutboxWorker` polls and publishes asynchronously.

```
Application Transaction:
  ┌─────────────────────────────────────────┐
  │  UPDATE orders SET status = 'CONFIRMED' │
  │  INSERT INTO outbox_messages (...)       │  ← atomic
  └─────────────────────────────────────────┘
              ↓ (commit)
  OutboxWorker (every 500ms):
    SELECT * FROM outbox_messages WHERE published = false LIMIT 100
    → kafkaTemplate.send(topic, payload).get(5s)
    → UPDATE outbox_messages SET published = true
```

### Outbox Worker — Distributed Instance Safety

When multiple Order Service instances run concurrently, each has its own `OutboxWorker`. To prevent duplicate publishing:

```sql
-- OutboxWorker uses SELECT FOR UPDATE SKIP LOCKED
-- Each instance claims a batch exclusively
SELECT * FROM outbox_messages
WHERE published = false
ORDER BY created_at
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

This ensures no two instances process the same outbox row simultaneously.

### Delivery Guarantee

- **At-least-once**: If OutboxWorker crashes after Kafka publish but before marking `published = true`, the message will be re-published on next poll
- **Consumers must be idempotent** — this is a system-wide contract, not optional

---

## 8. Concurrency Control

### Inventory: Optimistic Locking

Used for standard reservation scenarios where conflict rate is expected to be low (< 10%).

```
Thread A: SELECT qty=5, version=3
Thread B: SELECT qty=5, version=3

Thread A: UPDATE SET qty=4, version=4 WHERE version=3  → affected=1 ✅
Thread B: UPDATE SET qty=4, version=4 WHERE version=3  → affected=0 ❌
           → OptimisticLockException
           → retry (max 3, backoff 50ms/100ms/150ms)
           → re-SELECT qty=4, version=4
           → UPDATE SET qty=3, version=5 WHERE version=4  → affected=1 ✅
```

**When optimistic locking is NOT sufficient:**

Flash sale scenario (10,000 concurrent requests, 1 item) — retry loop creates thundering herd. For flash sale, use a **queue-based approach**: accept requests into a queue, process serially, reject overflow immediately. This is noted as a future enhancement.

### Order: Optimistic Locking on Aggregate

`orders.version` column prevents concurrent saga steps from overwriting each other:

```java
@Version
private Long version; // JPA adds AND version=? to every UPDATE
```

If two Kafka consumers attempt to update the same order concurrently (e.g., duplicate event delivery), only one succeeds. The other catches `OptimisticLockException` and skips (idempotency check ensures it's a no-op).

---

## 9. Idempotency Strategy

### Redis Key Convention

```
{service}:{eventType}:{aggregateId}

Examples:
  inventory:order-created:ord-uuid-123
  payment:payment-requested:ord-uuid-123
  notification:order-confirmed:ord-uuid-123
```

### Implementation Pattern

```java
@KafkaListener(topics = "orders.created")
public void onOrderCreated(OrderCreatedEvent event) {
    String key = "inventory:order-created:" + event.getOrderId();

    // SETNX — atomic set-if-not-exists
    Boolean isNew = redis.opsForValue()
        .setIfAbsent(key, "processed", Duration.ofHours(24));

    if (Boolean.FALSE.equals(isNew)) {
        log.warn("Duplicate event skipped: {}", key);
        return; // already processed
    }

    // process...
}
```

**TTL:** 24 hours — covers Kafka's max retry window.

**Note:** Redis key is set BEFORE processing, not after. This is intentional — if processing fails, the key expires and the event is retried on next delivery (at-least-once guarantee still holds).

---

## 10. Observability

### Structured Logging

Every log line MUST be JSON with these fields:

```json
{
  "timestamp": "2026-03-17T10:00:00.123Z",
  "level": "INFO",
  "service": "order-service",
  "correlationId": "uuid",
  "traceId": "uuid",
  "orderId": "uuid",
  "message": "Order transitioned from CONFIRMED to PAYMENT_PROCESSING"
}
```

MDC is set at the entry point of every HTTP request and Kafka consumer:

```java
MDC.put("correlationId", extractCorrelationId(record));
MDC.put("orderId", event.getOrderId().toString());
```

### Health Endpoints

Each service exposes:

```
GET /actuator/health        → service up/down + DB + Kafka connectivity
GET /actuator/prometheus    → Prometheus metrics scrape endpoint
```

### Key Metrics

| Metric | Type | Service | Description |
|---|---|---|---|
| `order_created_total` | Counter | Order | Total orders created |
| `order_saga_rollback_total` | Counter | Order | Total saga rollbacks |
| `order_status_transition_total` | Counter | Order | Per-status breakdown |
| `inventory_reservation_conflict_total` | Counter | Inventory | Optimistic lock conflicts |
| `inventory_reservation_success_total` | Counter | Inventory | Successful reservations |
| `payment_charge_total` | Counter | Payment | Charges attempted |
| `payment_failure_total` | Counter | Payment | Charges failed |
| `outbox_pending_messages` | Gauge | Order | Unpublished outbox messages |
| `kafka_consumer_lag` | Gauge | All | Consumer group lag per topic |
| `notification_sent_total` | Counter | Notification | Emails sent |
| `notification_failed_total` | Counter | Notification | Emails failed after retries |

---

## 11. Deployment Architecture

### Docker Compose Layout

```
docker-compose.yml
├── order-service          (port 8081)
├── inventory-service      (port 8082)
├── payment-service        (port 8083)
├── notification-service   (port 8084)
├── postgres-order         (port 5433)
├── postgres-inventory     (port 5434)
├── postgres-payment       (port 5435)
├── postgres-notification  (port 5436)
├── kafka                  (port 9092)
├── zookeeper              (port 2181)
└── redis                  (port 6379)
```

### Environment Variables (per service)

```env
# order-service/.env
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-order:5432/order_db
SPRING_DATASOURCE_USERNAME=order_user
SPRING_DATASOURCE_PASSWORD=secret

SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
KAFKA_CONSUMER_GROUP_ID=order-service

REDIS_HOST=redis
REDIS_PORT=6379

OUTBOX_WORKER_POLL_INTERVAL_MS=500
OUTBOX_WORKER_BATCH_SIZE=100

MOCK_PAYMENT_SUCCESS_RATE=0.9

LOG_LEVEL=INFO
```

### Service Startup Order

Services MUST start in this order (handled by `depends_on` in Docker Compose):

```
1. PostgreSQL instances (all 4)
2. Zookeeper
3. Kafka
4. Redis
5. Application services (all 4, parallel)
```

### Network Topology

```
docker network: ecommerce-net (bridge)

All containers on same network.
Services communicate by container name:
  order-service → postgres-order:5432
  order-service → kafka:9092
  order-service → redis:6379
```

---

## 12. Key Architecture Decisions

### ADR-001: Saga Orchestration over Choreography

**Decision:** Use Orchestration pattern with Order Service as central coordinator.

**Context:** Need to manage distributed transaction across 3 services with compensating transactions on failure.

**Rationale:**
- Choreography distributes saga logic across all services — harder to trace, harder to debug
- Orchestration centralizes control — one place to look when something goes wrong
- Team size (3 engineers) benefits from lower distributed complexity

**Trade-off:** Order Service becomes a more complex service. If it goes down, saga cannot progress — mitigated by Kafka event persistence (events are not lost, saga resumes on recovery).

---

### ADR-002: Outbox Pattern over Direct Kafka Publish

**Decision:** Persist events to `outbox_messages` table before publishing to Kafka.

**Context:** Publishing Kafka events directly after DB commit risks event loss if publish fails.

**Rationale:**
- Outbox + DB transaction = atomic event registration
- OutboxWorker provides at-least-once delivery guarantee
- Simpler than distributed transaction coordinator (XA/2PC)

**Trade-off:** Additional `outbox_messages` table per service. OutboxWorker adds ~500ms delay. `SELECT FOR UPDATE SKIP LOCKED` required for multi-instance safety.

---

### ADR-003: Optimistic Locking over Pessimistic for Inventory

**Decision:** Use `@Version` column for concurrency control on inventory reservation.

**Context:** Multiple concurrent orders may attempt to reserve the same product simultaneously.

**Rationale:**
- Pessimistic locking (`SELECT FOR UPDATE`) holds DB row lock for entire transaction duration — kills throughput under load
- Optimistic locking checks version only at write — no lock held during business logic execution
- Expected conflict rate is low for normal traffic (<10%)

**Trade-off:** Requires retry logic (max 3, backoff). Not suitable for flash sale scenarios where conflict rate approaches 100% — queue-based approach needed for that use case.

---

### ADR-004: Database per Service (No Shared DB)

**Decision:** Each service has its own PostgreSQL instance.

**Context:** Microservices need independent deployability and data isolation.

**Rationale:**
- Shared DB creates deployment coupling — schema changes in one service affect others
- Independent DB allows each service to evolve schema without coordination
- Prevents accidental cross-service joins which bypass service boundaries

**Trade-off:** No cross-service transactions. Data consistency achieved via Saga + eventual consistency. Joins across service data require API calls or event-driven denormalization.

---

### ADR-005: No Synchronous HTTP Between Services

**Decision:** All inter-service communication is async via Kafka. No REST calls from service to service.

**Context:** Synchronous calls create temporal coupling — if downstream service is slow or down, upstream is blocked.

**Rationale:**
- Kafka decouples producer availability from consumer availability
- Services can process at their own pace
- No cascading timeouts

**Trade-off:** No immediate consistency — Order Service does not know inventory reservation result synchronously. Saga adds latency to the full order flow (~1-3 seconds end-to-end). Debugging requires correlating logs across services by `correlationId`.


