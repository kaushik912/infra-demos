package com.example.outbox.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per domain event that needs to reach Kafka.
 *
 * It is written IN THE SAME DB TRANSACTION as the business change (the user row).
 * A separate relay later reads unprocessed rows and publishes them to Kafka.
 * That is the whole outbox pattern: the DB is the single source of truth, so the
 * "did the business change happen" and "did we record intent to publish" facts
 * commit atomically — no lost or phantom messages from a mid-flight crash.
 */
@Entity
@Table(name = "outbox_event",
        indexes = @Index(name = "idx_outbox_unprocessed", columnList = "processed, id"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "user" — the kind of aggregate this event is about. */
    @Column(nullable = false)
    private String aggregateType;

    /** e.g. the user id — used as the Kafka message key so a given user's events stay ordered. */
    @Column(nullable = false)
    private String aggregateId;

    /** e.g. "UserRegistered". */
    @Column(nullable = false)
    private String eventType;

    /** JSON payload published as the Kafka message value. */
    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    /** false = still needs publishing; flipped to true once Kafka has acked it. */
    @Column(nullable = false)
    private boolean processed;

    private Instant processedAt;

    protected OutboxEvent() {
        // required by JPA
    }

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.processed = false;
    }

    public void markProcessed() {
        this.processed = true;
        this.processedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isProcessed() {
        return processed;
    }
}