package com.example.outbox.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The "relay" (a.k.a. message relay / polling publisher). It periodically drains
 * the outbox table and forwards each event to Kafka, then marks it processed.
 *
 * Ordering matters: we wait for the broker to ACK BEFORE marking the row
 * processed. If the app crashes after the send but before the DB commit, the row
 * stays unprocessed and gets re-sent next tick — hence at-least-once delivery.
 * (Consumers should therefore be idempotent, e.g. key on eventType+userId.)
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    public static final String TOPIC = "user-events";
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000) // poll every 2s (after the previous run finishes)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findByProcessedFalseOrderByIdAsc(Limit.of(BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }
        log.info("[relay] found {} unprocessed outbox event(s)", batch.size());

        for (OutboxEvent event : batch) {
            try {
                SendResult<String, String> result = kafkaTemplate
                        .send(TOPIC, event.getAggregateId(), event.getPayload())
                        .get(10, TimeUnit.SECONDS); // block until Kafka acks

                event.markProcessed();
                log.info("[relay] published outbox id={} to {}-{} @offset {}",
                        event.getId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } catch (Exception e) {
                // leave it unprocessed; next tick retries. Stop the batch so we
                // don't reorder this aggregate's events past a failure.
                log.warn("[relay] failed to publish outbox id={}, will retry", event.getId(), e);
                break;
            }
        }
        // processed flags flushed when this @Transactional method commits
    }
}