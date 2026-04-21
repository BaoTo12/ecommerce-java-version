package com.ecommerce.order.integration;

import com.ecommerce.order.model.entity.IdempotencyKey;
import com.ecommerce.order.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    private final IdempotencyRepository repository;

    public IdempotencyStore(IdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns true if event is NEW (successfully acquired lock).
     * Returns false if event was already processed (duplicate).
     * 
     * Uses REQUIRES_NEW to ensure the idempotency key is committed even if the 
     * main transaction fails later, or to allow us to catch the exception 
     * without rolling back the main transaction (depending on how it's called).
     * 
     * Actually, if we use it INSIDE a transaction, and it fails, it might 
     * mark the transaction for rollback. Better use a separate transaction 
     * or handle the exception carefully.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String service, String eventType, String aggregateId) {
        String key = "%s:%s:%s".formatted(service, eventType, aggregateId);
        try {
            repository.saveAndFlush(IdempotencyKey.builder().key(key).build());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event skipped: {}", key);
            return false;
        }
    }
}
