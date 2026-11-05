package com.example.cdc.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads Debezium change events from cdc.public.policy.
 * Envelope (schemas disabled): {before, after, source, op, ts_ms}; op = c|u|d|r(snapshot).
 * Delivery is at-least-once, so real handlers must be idempotent.
 */
@Component
public class PolicyChangeConsumer {

    private static final Logger log = LoggerFactory.getLogger(PolicyChangeConsumer.class);

    private final ObjectMapper mapper;

    public PolicyChangeConsumer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @KafkaListener(topics = "cdc.public.policy")
    public void onChange(String value) {
        log.info(describe(value));
    }

    /** Pure parsing, kept separate so it is unit-testable without Kafka. */
    String describe(String json) {
        JsonNode event = mapper.readTree(json);
        String op = event.path("op").asString("?");
        JsonNode before = event.path("before");
        JsonNode after = event.path("after");
        return switch (op) {
            case "c" -> "INSERT " + summary(after);
            case "r" -> "SNAPSHOT " + summary(after);
            case "u" -> "UPDATE " + (before.isNull() || before.isMissingNode()
                    ? "(no before image; set REPLICA IDENTITY FULL) " : summary(before) + " -> ") + summary(after);
            case "d" -> "DELETE " + summary(before);
            default -> "UNKNOWN op=" + op;
        };
    }

    private static String summary(JsonNode row) {
        return "[id=" + row.path("id").asString("?")
                + ", number=" + row.path("policy_number").asString("?")
                + ", holder=" + row.path("policy_holder").asString("?") + "]";
    }
}
