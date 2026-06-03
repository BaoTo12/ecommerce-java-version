package com.ecommerce.monolith.infrastructure.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetterMessageEntity, UUID> {
  long countByReplayedAtIsNull();
}
