package com.ecommerce.monolith.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edge Case #11 — Outbox Pattern: Write side
 *
 * <p>Must be called WITHIN the same transaction as the business operation. The outbox row is
 * committed atomically with the business data.
 */
@Service
public class OutboxService {

  private final OutboxRepository repository;
  private final ObjectMapper objectMapper;

  public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /**
   * Saves an event to the outbox table. Must be called inside an active transaction (same as the
   * main business tx).
   *
   * @param eventType Simple class name of the event (e.g., "OrderCreatedEvent")
   * @param aggregateId ID of the root aggregate (e.g., orderId)
   * @param payload The event object — will be serialized to JSON
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void save(String eventType, String aggregateId, Object payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      OutboxMessageEntity msg = OutboxMessageEntity.of(eventType, aggregateId, json);
      repository.save(msg);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write to outbox for event: " + eventType, e);
    }
  }
}
