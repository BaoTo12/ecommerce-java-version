package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.OutboxMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessageEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT m FROM OutboxMessageEntity m WHERE m.published = false ORDER BY m.createdAt ASC LIMIT :batchSize")
    List<OutboxMessageEntity> findUnpublishedForUpdate(int batchSize);
}
