package com.example.outbox.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A downstream subscriber. In real life this would live in another service
 * (email, analytics, search indexer, ...). Here it just logs, to prove the
 * message fanned out through Kafka after the outbox relay published it.
 */
@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    @KafkaListener(topics = "user-events", groupId = "demo-consumer")
    public void onUserEvent(String message) {
        log.info("[consumer] >>> received from Kafka: {}", message);
    }
}