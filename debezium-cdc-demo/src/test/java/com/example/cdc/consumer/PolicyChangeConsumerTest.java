package com.example.cdc.consumer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** No Docker/Kafka needed: only the envelope parsing. */
class PolicyChangeConsumerTest {

    private final PolicyChangeConsumer consumer = new PolicyChangeConsumer(JsonMapper.builder().build());

    @Test
    void insert() {
        String json = """
                {"before":null,"after":{"id":1,"policy_number":"P-1","policy_holder":"Ann"},"op":"c"}""";
        assertThat(consumer.describe(json)).isEqualTo("INSERT [id=1, number=P-1, holder=Ann]");
    }

    @Test
    void updateWithBeforeImage() {
        String json = """
                {"before":{"id":1,"policy_number":"P-1","policy_holder":"Ann"},
                 "after":{"id":1,"policy_number":"P-1","policy_holder":"Bob"},"op":"u"}""";
        assertThat(consumer.describe(json))
                .isEqualTo("UPDATE [id=1, number=P-1, holder=Ann] -> [id=1, number=P-1, holder=Bob]");
    }

    @Test
    void updateWithoutBeforeImageHintsReplicaIdentity() {
        String json = """
                {"before":null,"after":{"id":1,"policy_number":"P-1","policy_holder":"Bob"},"op":"u"}""";
        assertThat(consumer.describe(json)).contains("REPLICA IDENTITY FULL");
    }

    @Test
    void delete() {
        String json = """
                {"before":{"id":1,"policy_number":"P-1","policy_holder":"Ann"},"after":null,"op":"d"}""";
        assertThat(consumer.describe(json)).isEqualTo("DELETE [id=1, number=P-1, holder=Ann]");
    }
}
