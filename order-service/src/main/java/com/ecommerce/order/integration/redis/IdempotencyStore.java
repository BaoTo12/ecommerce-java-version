package com.ecommerce.order.integration.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Collections;

@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    private final Set<String> processedEvents = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public IdempotencyStore() {
    }

    /**
     * Returns true if event is NEW (not a duplicate).
     * Returns false if event was already processed (duplicate).
     */
    public boolean tryAcquire(String service, String eventType, String aggregateId) {
        String key = "%s:%s:%s".formatted(service, eventType, aggregateId);
        if (!processedEvents.add(key)) {
            log.warn("Duplicate event skipped: {}", key);
            return false;
        }
        return true;
    }
}
