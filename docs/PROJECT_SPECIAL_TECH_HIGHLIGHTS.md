# Project Special Tech Highlights

## Overview
This project is not a basic CRUD e-commerce app. It is designed as a modern distributed system with production-style reliability patterns for consistency, concurrency, and failure handling.

## What Is Special About This Project

### 1. Saga Orchestration for Distributed Transactions
- Uses orchestration-based Saga with Order Service as the central coordinator.
- Coordinates multi-step workflows across Inventory, Payment, and Notification services.
- Applies compensating actions on failure (for example inventory release after payment failure).
- Avoids risky cross-service database transactions and supports eventual consistency at scale.

### 2. Outbox Pattern for Reliable Event Publishing
- Persists business state changes and event records in the same database transaction.
- Background Outbox Worker publishes pending messages to Kafka.
- Prevents event loss when database commit succeeds but broker publish fails.
- Supports safe multi-instance publishing using row-claim patterns such as skip-locked polling.

### 3. Optimistic Versioning for Concurrency Control
- Uses version columns to detect write conflicts in high-concurrency flows.
- Inventory reservation and order updates are protected from lost updates.
- Retry strategy handles transient write conflicts without long-held database locks.
- Database constraints remain the final safety net against invalid states.

### 4. Idempotency and Deduplication Across Event Consumers
- Assumes at-least-once message delivery and handles duplicates safely.
- Uses deterministic idempotency keys per service and event.
- Redis key-based deduplication prevents duplicate processing effects.
- Database uniqueness constraints provide an additional backstop.

### 5. Event-Driven Microservices with Clear Boundaries
- Strict database-per-service ownership.
- Services communicate asynchronously through Kafka topics.
- No cross-service direct database access.
- Decoupled design improves resilience and independent deployment.

### 6. Strong Auditability and Traceability
- Append-only order status history for full lifecycle auditing.
- Correlation IDs propagated across requests, events, and logs.
- Structured logging supports cross-service debugging and incident analysis.

### 7. Resilience-Oriented Failure Handling
- Explicit handling for inventory, payment, and notification failure paths.
- Compensating transactions keep business outcomes consistent.
- Retries with bounded attempts and backoff reduce transient failure impact.
- Manual intervention paths are defined for non-recoverable states.

### 8. Production-Ready Data and Operations Design
- PostgreSQL schemas include constraints, partial indexes, and status-safe checks.
- Health endpoints and metrics support operations visibility.
- Outbox backlog, consumer lag, and rollback counters support runtime monitoring.
- Containerized deployment model with service isolation.

## Why This Matters
These patterns move the system from simple feature completeness to production-grade reliability. The design demonstrates practical handling of real distributed-systems problems:
- partial failures
- duplicate events
- race conditions
- state consistency across services
- observability and recovery

## Summary
Core advanced capabilities in this project:
- Saga orchestration
- Outbox-based reliable messaging
- Optimistic locking with versioning
- Deduplication and idempotent consumers
- Append-only audit trail
- End-to-end observability and operational metrics
