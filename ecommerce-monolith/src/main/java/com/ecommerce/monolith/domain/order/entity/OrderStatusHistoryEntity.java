package com.ecommerce.monolith.domain.order.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Edge Case #29 — Audit Log Immutability
 *
 * <p>Problem: Audit logs are only useful if they can be trusted. If anyone (a bug, a rogue
 * developer, a SQL script) can UPDATE or DELETE audit records, the audit trail is worthless for
 * compliance and debugging.
 *
 * <p>Real incident: Developer runs "UPDATE order_status_history SET to_status='COMPLETED'" on a
 * batch of orders to "fix" a data issue. Audit trail now lies. Compliance fails.
 *
 * <p>Solution: THREE layers of immutability:
 *
 * <p>Layer 1 — @Immutable (Hibernate): Hibernate never generates UPDATE statements for this entity.
 * Even if code calls `historyRepo.save(existing)`, Hibernate silently ignores changes. This is not
 * an exception — it just does nothing, which is correct behavior.
 *
 * <p>Layer 2 — No setters: No setter methods exist on this class. You literally cannot mutate it in
 * code without reflection. Makes it structurally impossible to accidentally modify.
 *
 * <p>Layer 3 — DB-level: PostgreSQL ROW SECURITY POLICY (documented in schema): CREATE POLICY
 * audit_no_update ON order_status_history FOR UPDATE USING (false); -- no row can ever be updated
 *
 * <p>CREATE POLICY audit_no_delete ON order_status_history FOR DELETE USING (false); -- no row can
 * ever be deleted
 *
 * <p>This prevents even direct SQL from modifying records. (Admins can bypass RLS with BYPASSRLS
 * role — documented and audited.)
 *
 * <p>Why not just an @Column(updatable=false) on each field? That only prevents Hibernate from
 * updating, but @Immutable prevents the entire entity from being updated — more comprehensive.
 */
@Entity
@Immutable // Layer 1: Hibernate never issues UPDATEs for this entity
@Table(
    name = "order_status_history",
    indexes = {@Index(name = "idx_order_history_order", columnList = "order_id")})
public class OrderStatusHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false) // Layer 2
  private UUID orderId;

  @Column(name = "from_status", length = 30, updatable = false)
  private String fromStatus;

  @Column(name = "to_status", nullable = false, length = 30, updatable = false)
  private String toStatus;

  @Column(length = 500, updatable = false)
  private String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected OrderStatusHistoryEntity() {}

  /** Factory method — the ONLY way to create a history record. No setters. */
  public static OrderStatusHistoryEntity of(
      UUID orderId, String fromStatus, String toStatus, String reason) {
    OrderStatusHistoryEntity h = new OrderStatusHistoryEntity();
    h.orderId = orderId;
    h.fromStatus = fromStatus;
    h.toStatus = toStatus;
    h.reason = reason;
    h.createdAt = Instant.now();
    return h;
  }

  // ─── Read-only getters only — no setters ─────────────────────────────────
  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public String getFromStatus() {
    return fromStatus;
  }

  public String getToStatus() {
    return toStatus;
  }

  public String getReason() {
    return reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
