package com.ecommerce.order.worker;

import com.ecommerce.order.model.entity.OutboxMessageEntity;
import com.ecommerce.order.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxWorker(OutboxRepository outboxRepo, KafkaTemplate<String, String> kafkaTemplate,
                        @Value("${app.outbox.batch-size:100}") int batchSize) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingMessages() {
        List<OutboxMessageEntity> pending = outboxRepo.findUnpublishedForUpdate(batchSize);
        if (pending.isEmpty()) return;

        for (OutboxMessageEntity msg : pending) {
            try {
                kafkaTemplate.send(msg.getTopic(), msg.getAggregateId(), msg.getPayload())
                        .get(5, TimeUnit.SECONDS);
                msg.markPublished();
                outboxRepo.save(msg);
                log.debug("Published outbox message: id={}, type={}, topic={}", msg.getId(), msg.getEventType(), msg.getTopic());
            } catch (Exception e) {
                msg.markFailed(e.getMessage());
                outboxRepo.save(msg);
                log.error("Failed to publish outbox message: id={}, error={}", msg.getId(), e.getMessage());
            }
        }

        log.info("OutboxWorker processed {} messages", pending.size());
    }
}
