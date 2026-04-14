package com.ecommerce.order.service;

import com.ecommerce.order.model.entity.OutboxMessageEntity;
import com.ecommerce.order.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepo, ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    public void saveEvent(String eventType, UUID aggregateId, String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxMessageEntity message = OutboxMessageEntity.of(eventType, aggregateId.toString(), topic, json);
            outboxRepo.save(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event: " + eventType, e);
        }
    }
}
