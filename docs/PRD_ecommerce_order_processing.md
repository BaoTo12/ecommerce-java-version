# PRD — E-commerce Order Processing System

**Version:** 1.0.0
**Status:** Draft
**Owner:** Product Team
**Last Updated:** 2026-03-17
**Target Audience:** Internal Dev Team (Backend Engineers)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Goals & Success Metrics](#2-goals--success-metrics)
3. [System Context](#3-system-context)
4. [Epics & User Stories](#4-epics--user-stories)
   - [Epic 1 — Order Service](#epic-1--order-service)
   - [Epic 2 — Inventory Service](#epic-2--inventory-service)
   - [Epic 3 — Payment Service](#epic-3--payment-service)
   - [Epic 4 — Notification Service](#epic-4--notification-service)
5. [Functional Requirements](#5-functional-requirements)
6. [Non-Functional Requirements](#6-non-functional-requirements)
7. [Constraints & Assumptions](#7-constraints--assumptions)
8. [Out of Scope](#8-out-of-scope)
9. [Glossary](#9-glossary)

---

## 1. Overview

### Problem Statement

E-commerce order processing involves multiple distributed services that must coordinate atomically — create order, reserve inventory, charge payment, and notify customer — across unreliable networks and concurrent requests.

A naive implementation leads to:
- **Overselling** — inventory deducted multiple times due to race conditions
- **Inconsistent state** — order marked PAID but inventory never reserved
- **Silent failures** — payment charged but no order record exists
- **Zero traceability** — no audit trail when production incidents occur

### Solution

Build an **event-driven, microservices-based order processing system** using the **Saga Orchestration pattern** to coordinate distributed transactions across four services: Order, Inventory, Payment, and Notification, while extending existing services with demo-focused catalog, cart, checkout, and query APIs.

### In Scope for v1.0

| Service | Responsibility |
|---|---|
| Order Service | Catalog APIs, cart APIs, checkout orchestration, state machine, saga orchestration, audit trail, query APIs |
| Inventory Service | Stock reservation, optimistic locking, compensating release |
| Payment Service | Charge processing, refund on rollback |
| Notification Service | Email/push delivery on order events |

---

## 2. Goals & Success Metrics

### Business Goals

- Allow customers to place orders with guaranteed consistency between order state, inventory, and payment
- Support concurrent orders without overselling
- Provide full audit trail for every order state transition

### Technical Goals

- Demonstrate Saga pattern with compensating transactions
- Implement optimistic locking on inventory
- Use Outbox Pattern to guarantee event delivery
- All inter-service communication via Kafka events

### Success Metrics

| Metric | Target |
|---|---|
| Order creation p99 latency | < 500ms |
| Inventory reservation accuracy under 100 concurrent requests | 0 oversell |
| Saga rollback success rate on payment failure | 100% |
| Event delivery guarantee | At-least-once |
| Order audit trail completeness | 100% state transitions logged |

---

## 3. System Context

```
Client
  │
  ▼
Order Service (Saga Orchestrator)
  │
  ├──[Kafka: order.created]──────────► Inventory Service
  │                                          │
  │◄──[Kafka: inventory.reserved]────────────┘
  │◄──[Kafka: inventory.failed]──────────────┘
  │
  ├──[Kafka: payment.requested]──────► Payment Service
  │                                          │
  │◄──[Kafka: payment.succeeded]─────────────┘
  │◄──[Kafka: payment.failed]────────────────┘
  │
  └──[Kafka: order.status.changed]───► Notification Service
```

### Technology Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 15 |
| Message Broker | Apache Kafka |
| Cache / Idempotency | Redis |
| Containerization | Docker + Docker Compose |
| Build Tool | Maven |

---

## 4. Epics & User Stories

---

### Epic 1 — Order Service

**Goal:** Manage the full lifecycle of an order from creation to completion or cancellation. Act as the Saga orchestrator coordinating all downstream services.

**Scope:** Catalog and cart APIs, checkout orchestration, order CRUD, state machine enforcement, saga orchestration, outbox event publishing, audit trail, and read-model/query APIs.

---

#### US-001 — Place an Order

> **As a** customer,
> **I want to** place an order with one or more items,
> **So that** the system reserves stock and initiates payment on my behalf.

**Acceptance Criteria:**

- [ ] `POST /orders` accepts a valid order payload `{ userId, items: [{ productId, quantity, unitPrice }] }`
- [ ] System validates that `items` is non-empty; returns `400 Bad Request` with error detail if empty
- [ ] System creates an `Order` entity with status `PENDING` and transitions immediately to `CONFIRMED` after validation
- [ ] System publishes `OrderCreatedEvent` to Kafka topic `order.created` via Outbox Pattern — within the same DB transaction
- [ ] Response returns `201 Created` with `{ orderId, status, totalAmount }`
- [ ] Each order is assigned a globally unique `orderId` (UUID v4)

**Technical Notes:**
- Outbox table write + order save must be in the same `@Transactional` boundary
- Do NOT publish to Kafka directly inside the transaction — use the Outbox Worker

---

#### US-002 — View Order Details

> **As a** customer,
> **I want to** query the current state of my order,
> **So that** I know whether it has been confirmed, paid, or cancelled.

**Acceptance Criteria:**

- [ ] `GET /orders/{orderId}` returns order with fields: `id`, `userId`, `status`, `items`, `totalAmount`, `createdAt`, `updatedAt`
- [ ] Returns `404 Not Found` if `orderId` does not exist
- [ ] Returns `403 Forbidden` if `userId` in token does not match order's `userId`
- [ ] Response includes current `status` reflecting the latest state machine transition

---

#### US-003 — Cancel an Order

> **As a** customer,
> **I want to** cancel my order before payment is processed,
> **So that** I am not charged and inventory is released.

**Acceptance Criteria:**

- [ ] `DELETE /orders/{orderId}` cancels the order
- [ ] Cancellation only allowed from states: `PENDING`, `CONFIRMED`
- [ ] Returns `409 Conflict` with reason if order is in `PAYMENT_PROCESSING`, `PAID`, `COMPLETED`, or `CANCELLED`
- [ ] System publishes `OrderCancelledEvent` to trigger inventory release
- [ ] Order status transitions to `CANCELLED` with reason `"Cancelled by customer"`

---

#### US-004 — View Order History

> **As a** customer,
> **I want to** see all state transitions for my order with timestamps,
> **So that** I have a full audit trail of what happened.

**Acceptance Criteria:**

- [ ] `GET /orders/{orderId}/history` returns ordered list of status transitions
- [ ] Each entry includes: `fromStatus`, `toStatus`, `reason`, `timestamp`
- [ ] History is append-only — no entries are ever deleted
- [ ] Returns events in ascending chronological order

---

#### US-005 — Saga: Handle Inventory Result

> **As the** Order Service (Saga Orchestrator),
> **I want to** react to inventory reservation outcomes,
> **So that** I can proceed to payment or rollback the order.

**Acceptance Criteria:**

- [ ] On `InventoryReservedEvent`: transition order to `PAYMENT_PROCESSING`, publish `PaymentRequestedEvent`
- [ ] On `InventoryFailedEvent`: transition order to `CANCELLED`, reason = `"Insufficient stock"`
- [ ] Consumer is idempotent — processing the same event twice produces no side effects
- [ ] Consumer logs `correlationId` extracted from Kafka message header

---

#### US-006 — Saga: Handle Payment Result

> **As the** Order Service (Saga Orchestrator),
> **I want to** react to payment outcomes,
> **So that** I can complete the order or trigger a rollback including inventory release.

**Acceptance Criteria:**

- [ ] On `PaymentSucceededEvent`: transition order to `PAID`, then `FULFILLING`, publish `OrderCompletedEvent`
- [ ] On `PaymentFailedEvent`: transition order to `PAYMENT_FAILED`, then `CANCELLED`, publish `InventoryReleaseRequestedEvent`
- [ ] Compensating events published via Outbox — not fire-and-forget
- [ ] All transitions persisted to `order_status_history` table

---

#### US-017 — Catalog APIs in Existing Order Service

> **As a** customer,
> **I want to** browse available products,
> **So that** I can decide what to add to cart.

**Acceptance Criteria:**

- [ ] `GET /catalog/products` supports pagination and keyword search
- [ ] `GET /catalog/products/{productId}` returns product detail and price snapshot fields
- [ ] Inactive products are hidden from customer-facing responses

---

#### US-018 — Cart APIs in Existing Order Service

> **As a** customer,
> **I want to** manage my cart before placing an order,
> **So that** checkout uses the latest selected items.

**Acceptance Criteria:**

- [ ] `POST /cart/items` adds item to active cart or increments quantity if product already exists
- [ ] `PATCH /cart/items/{itemId}` updates quantity with validation (`quantity > 0`)
- [ ] `DELETE /cart/items/{itemId}` removes item from cart
- [ ] `GET /cart` returns cart items with subtotal

---

#### US-020 — Checkout Orchestration in Existing Order Service

> **As a** customer,
> **I want to** checkout my active cart,
> **So that** the system creates an order and starts the saga flow.

**Acceptance Criteria:**

- [ ] `POST /checkout` validates cart ownership and non-empty cart
- [ ] Checkout creates `checkout_session` with status `CREATED` then `PROCESSING`
- [ ] On success, checkout creates order and transitions cart to `CHECKED_OUT`
- [ ] On failure, checkout marks session `FAILED` with reason and cart remains `ACTIVE`
- [ ] Checkout response returns `{ checkoutSessionId, orderId, status }`

---

#### US-019 — Read-Model and Query APIs

> **As a** customer or admin,
> **I want to** query optimized order views,
> **So that** timeline and list APIs are fast and stable.

**Acceptance Criteria:**

- [ ] Order Service maintains `order_read_model` projection table
- [ ] `GET /orders` supports pagination and status filtering
- [ ] `GET /orders/{orderId}/timeline` returns combined order and payment milestones
- [ ] Projection updates are idempotent and eventually consistent

---

### Epic 2 — Inventory Service

**Goal:** Manage product stock with guaranteed consistency under concurrent reservation requests. Participate in Saga as a downstream service.

**Scope:** Stock reservation, release (compensating transaction), optimistic locking, stock query.

---

#### US-007 — Reserve Inventory

> **As the** Inventory Service,
> **I want to** atomically reserve stock when an order is created,
> **So that** no two orders can reserve the same physical unit.

**Acceptance Criteria:**

- [ ] Consumes `OrderCreatedEvent` from topic `order.created`
- [ ] Decrements `quantity` for each `productId` in the order atomically
- [ ] Uses optimistic locking (`@Version` column) — no `SELECT FOR UPDATE`
- [ ] If `quantity` after decrement would go below 0: abort, publish `InventoryFailedEvent` with reason `"Insufficient stock"`
- [ ] If reservation succeeds: publish `InventoryReservedEvent`
- [ ] Consumer is idempotent via Redis idempotency key `inventory:reserved:{orderId}`
- [ ] `CHECK (quantity >= 0)` constraint exists at DB level as last line of defense

**Technical Notes:**
- Retry up to 3 times on `OptimisticLockException` with 50ms backoff
- After 3 failed retries: publish `InventoryFailedEvent` with reason `"Concurrent conflict"`

---

#### US-008 — Release Inventory (Compensating Transaction)

> **As the** Inventory Service,
> **I want to** release previously reserved stock when a Saga rollback is triggered,
> **So that** the stock becomes available again for future orders.

**Acceptance Criteria:**

- [ ] Consumes `InventoryReleaseRequestedEvent` from topic `inventory.release.requested`
- [ ] Increments `quantity` for each `productId` by the originally reserved amount
- [ ] If `productId` not found: log error, publish `InventoryReleaseFailedEvent` — do not silently swallow
- [ ] Idempotent: releasing the same `orderId` twice has no effect
- [ ] Publishes `InventoryReleasedEvent` on success

---

#### US-009 — Query Stock Level

> **As an** internal service or admin,
> **I want to** query current stock for a product,
> **So that** I can display availability to customers or trigger restocking.

**Acceptance Criteria:**

- [ ] `GET /inventory/{productId}` returns `{ productId, quantity, version }`
- [ ] Returns `404 Not Found` if product does not exist
- [ ] Read does not acquire any DB lock
- [ ] Response reflects committed data — no dirty reads

---

### Epic 3 — Payment Service

**Goal:** Process payment charges and refunds as part of the Saga. Mock external payment gateway for v1.0.

**Scope:** Charge on order, refund on rollback, idempotency, mock gateway integration.

---

#### US-010 — Process Payment

> **As the** Payment Service,
> **I want to** charge the customer when the Order Service requests payment,
> **So that** revenue is collected for confirmed orders.

**Acceptance Criteria:**

- [ ] Consumes `PaymentRequestedEvent` from topic `payment.requested`
- [ ] Calls mock payment gateway with `{ orderId, userId, amount }`
- [ ] On gateway success: persist payment record with status `CHARGED`, publish `PaymentSucceededEvent`
- [ ] On gateway failure: persist payment record with status `FAILED`, publish `PaymentFailedEvent` with `failureReason`
- [ ] Idempotent via Redis key `payment:processed:{orderId}` — duplicate events produce no second charge
- [ ] Payment record stores: `orderId`, `userId`, `amount`, `status`, `gatewayTransactionId`, `createdAt`

**Technical Notes:**
- Mock gateway: 90% success, 10% random failure — configurable via environment variable
- Real gateway integration is out of scope for v1.0

---

#### US-011 — Refund Payment (Compensating Transaction)

> **As the** Payment Service,
> **I want to** refund a previously charged payment when the Saga rolls back,
> **So that** the customer is not charged for a failed order.

**Acceptance Criteria:**

- [ ] Consumes `PaymentRefundRequestedEvent` (emitted by Order Service on saga rollback after `PAID` state)
- [ ] Calls mock gateway refund with original `gatewayTransactionId`
- [ ] On success: update payment record status to `REFUNDED`, publish `PaymentRefundedEvent`
- [ ] On failure: update status to `REFUND_FAILED`, publish `PaymentRefundFailedEvent` — requires manual intervention flag
- [ ] Idempotent: refunding the same `orderId` twice has no effect

---

#### US-012 — Query Payment Status

> **As an** internal service,
> **I want to** query the payment status for an order,
> **So that** the Order Service can verify payment state during recovery scenarios.

**Acceptance Criteria:**

- [ ] `GET /payments/{orderId}` returns `{ orderId, status, amount, gatewayTransactionId, createdAt }`
- [ ] Returns `404 Not Found` if no payment record exists for `orderId`
- [ ] Only accessible internally — not exposed via public API gateway

---

### Epic 4 — Notification Service

**Goal:** Deliver timely, accurate notifications to customers at key order lifecycle events.

**Scope:** Email notifications, event-driven triggering, delivery tracking, retry on failure.

---

#### US-013 — Notify Order Confirmed

> **As a** customer,
> **I want to** receive a confirmation notification when my order is confirmed,
> **So that** I know the system has accepted my order.

**Acceptance Criteria:**

- [ ] Consumes `OrderCreatedEvent` from topic `order.created`
- [ ] Sends email to customer with: `orderId`, list of items, `totalAmount`, estimated processing time
- [ ] Email sent within 30 seconds of event consumption
- [ ] Notification record persisted with status `SENT` or `FAILED`
- [ ] On send failure: retry up to 3 times with exponential backoff (1s, 2s, 4s)
- [ ] After 3 failures: mark record `FAILED`, do not retry — alert monitoring

---

#### US-014 — Notify Order Completed

> **As a** customer,
> **I want to** receive a notification when my order is completed,
> **So that** I know my purchase was successful.

**Acceptance Criteria:**

- [ ] Consumes `OrderCompletedEvent` from topic `order.status.changed`
- [ ] Sends email: order summary, payment confirmation, `gatewayTransactionId`
- [ ] Idempotent: duplicate events do not send duplicate emails (idempotency key: `notification:completed:{orderId}`)

---

#### US-015 — Notify Order Cancelled

> **As a** customer,
> **I want to** receive a notification when my order is cancelled,
> **So that** I understand the reason and can take action.

**Acceptance Criteria:**

- [ ] Consumes `OrderCancelledEvent` from topic `order.status.changed`
- [ ] Email includes: `orderId`, cancellation `reason`, refund status if payment was charged
- [ ] Differentiates cancellation reason: `"Cancelled by customer"` vs `"Insufficient stock"` vs `"Payment failed"`
- [ ] Idempotent: `notification:cancelled:{orderId}`

---

#### US-016 — Notify Payment Failed

> **As a** customer,
> **I want to** receive a notification when my payment fails,
> **So that** I can retry with a different payment method.

**Acceptance Criteria:**

- [ ] Consumes `PaymentFailedEvent` from topic `payment.failed`
- [ ] Email includes: failure reason, suggestion to retry, link to order
- [ ] Sent within 60 seconds of event consumption
- [ ] Does not expose raw gateway error codes to customer — map to user-friendly message

---

## 5. Functional Requirements

### FR-001 — Order State Machine

The Order Service MUST enforce the following valid state transitions. Any attempt to transition outside this set MUST throw `IllegalStateTransitionException` and return `409 Conflict`.

| From | Allowed Next States |
|---|---|
| `PENDING` | `CONFIRMED`, `CANCELLED` |
| `CONFIRMED` | `PAYMENT_PROCESSING`, `CANCELLED` |
| `PAYMENT_PROCESSING` | `PAID`, `PAYMENT_FAILED` |
| `PAID` | `FULFILLING` |
| `PAYMENT_FAILED` | `CANCELLED` |
| `FULFILLING` | `COMPLETED` |
| `COMPLETED` | *(terminal — no transitions)* |
| `CANCELLED` | *(terminal — no transitions)* |

### FR-002 — Outbox Pattern

All domain events MUST be persisted to the `outbox_messages` table within the same DB transaction as the state change. A separate `OutboxWorker` polls unpublished messages every 500ms and publishes to Kafka.

Direct Kafka publishing inside `@Transactional` methods is PROHIBITED.

### FR-003 — Idempotency

All Kafka consumers MUST be idempotent. Each consumer checks a Redis key before processing. Key format: `{service}:{eventType}:{aggregateId}`. TTL: 24 hours.

### FR-004 — Correlation ID

Every HTTP request MUST carry an `X-Correlation-ID` header. If absent, the service generates one. The correlation ID MUST be:
- Propagated in all Kafka event headers as `correlationId`
- Injected into MDC at the start of every Kafka consumer
- Included in every log line

### FR-005 — Audit Trail

Every order state transition MUST produce a record in `order_status_history` with: `orderId`, `fromStatus`, `toStatus`, `reason`, `createdAt`. This table is append-only — UPDATE and DELETE operations are PROHIBITED.

### FR-006 — Database Constraints

The following DB-level constraints are REQUIRED as last line of defense:

```sql
CHECK (quantity >= 0)         -- inventory table
CHECK (amount > 0)            -- payments table
NOT NULL on all FK columns
```

---

## 6. Non-Functional Requirements

### NFR-001 — Performance

| Requirement | Target |
|---|---|
| Order creation p99 latency | ≤ 500ms |
| Inventory reservation p99 latency | ≤ 200ms |
| Kafka event consumption lag | ≤ 1 second under normal load |
| Outbox Worker publish delay | ≤ 1 second from DB commit |

### NFR-002 — Reliability

| Requirement | Target |
|---|---|
| Order Service availability | ≥ 99.5% |
| Event delivery guarantee | At-least-once (Kafka + Outbox) |
| Saga rollback success rate | 100% — compensating transactions must never be skipped |
| Zero oversell under concurrent load | 0 violations in load test with 100 concurrent requests on 1-unit stock |

### NFR-003 — Scalability

- Each service MUST be horizontally scalable — no in-memory shared state between instances
- Kafka consumer groups MUST be configured so that adding instances increases throughput linearly
- PostgreSQL connections MUST be managed via connection pool (HikariCP) — max pool size configurable per environment

### NFR-004 — Observability

- All services MUST emit structured JSON logs with fields: `timestamp`, `level`, `service`, `correlationId`, `message`, `traceId`
- All services MUST expose a `/actuator/health` endpoint returning service + DB + Kafka connectivity
- Key business metrics MUST be exposed via Prometheus endpoint `/actuator/prometheus`:
  - `order_created_total` (counter)
  - `order_saga_rollback_total` (counter)
  - `inventory_reservation_conflict_total` (counter)
  - `kafka_consumer_lag` (gauge)
  - `outbox_pending_messages` (gauge)

### NFR-005 — Security

- All inter-service HTTP endpoints (if any) MUST be authenticated via internal API key header `X-Internal-Api-Key`
- Customer-facing endpoints MUST validate JWT token — `userId` claim used to scope data access
- No sensitive data (card numbers, raw gateway credentials) stored in DB or logs

### NFR-006 — Consistency Model

- Intra-service operations: **strong consistency** (ACID via PostgreSQL)
- Inter-service operations: **eventual consistency** (Saga + Kafka at-least-once)
- Consumers MUST tolerate duplicate events — idempotency is mandatory, not optional

### NFR-007 — Data Retention

- `orders` and `order_status_history`: retained indefinitely
- `outbox_messages`: published records purged after 7 days via scheduled job
- `notifications`: retained for 90 days
- Kafka topic retention: 7 days

### NFR-008 — Deployment

- All services MUST be containerized via Docker
- `docker-compose.yml` MUST bring up all services + PostgreSQL + Kafka + Redis with a single command
- Environment-specific configuration via `.env` files — no hardcoded credentials in source code
- Each service MUST support graceful shutdown — in-flight Kafka messages MUST be committed before process exits

### NFR-009 — Testability

- Unit tests: Service/model business rules (state machine, order logic) — no full Spring context required
- Integration tests: Service + Repository layer — real PostgreSQL via Testcontainers
- Concurrent tests: Inventory reservation — 100 concurrent threads, assert 0 oversell
- Contract tests (optional): Kafka event schemas validated via schema registry or JSON Schema

## 7. Constraints & Assumptions

| Item | Detail |
|---|---|
| Payment gateway | Mock only for v1.0 — real Stripe/VNPay integration is out of scope |
| Authentication | JWT validation assumed — auth service is out of scope |
| Frontend | No UI — all interactions via REST API or Kafka |
| Database | One PostgreSQL instance per service — no shared DB |
| Kafka | Single-node Kafka acceptable for dev/test; production clustering is out of scope |
| Team size | 3 engineers — services split across team members |
| Timeline | 3 months — phased delivery (see Epic priority order) |

---

## 8. Out of Scope

The following are explicitly NOT part of v1.0:

- Real payment gateway integration (Stripe, VNPay, MoMo)
- Customer-facing UI or mobile app
- Product catalog management
- Multi-currency support
- Admin dashboard
- CQRS / Event Sourcing (may be considered for v2.0)
- Rate limiting and DDoS protection
- Multi-region deployment

---

## 9. Glossary

| Term | Definition |
|---|---|
| **Saga** | A pattern for managing distributed transactions via a sequence of local transactions with compensating actions on failure |
| **Orchestration** | Saga variant where a central coordinator (Order Service) directs each participant step-by-step |
| **Compensating Transaction** | A business operation that semantically undoes a previously committed step (e.g., releasing reserved inventory) |
| **Outbox Pattern** | Persisting domain events to an `outbox_messages` table within the same DB transaction as the state change, then publishing to Kafka asynchronously — guarantees no event is lost on publish failure |
| **Optimistic Locking** | A concurrency strategy where a `version` column is checked on every UPDATE — if the version has changed since the row was read, the UPDATE fails with 0 affected rows, indicating a concurrent modification |
| **Idempotency** | The property of an operation that produces the same result regardless of how many times it is executed — required for all Kafka consumers due to at-least-once delivery |
| **At-least-once Delivery** | Kafka's default guarantee — a message may be delivered more than once; consumers must handle duplicates |
| **Correlation ID** | A unique identifier assigned per request that is propagated across all service calls and Kafka events for end-to-end tracing |
| **State Machine** | A model that defines the set of valid states an entity can be in and the allowed transitions between those states |
| **Terminal State** | A state from which no further transitions are possible (`COMPLETED`, `CANCELLED`) |
| **DLQ (Dead Letter Queue)** | A Kafka topic where messages that cannot be processed after max retries are sent for manual inspection |
