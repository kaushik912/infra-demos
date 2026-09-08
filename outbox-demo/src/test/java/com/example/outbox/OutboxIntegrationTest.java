package com.example.outbox;

import com.example.outbox.outbox.OutboxRepository;
import com.example.outbox.registration.RegisterRequest;
import com.example.outbox.registration.RegistrationService;
import com.example.outbox.user.AppUser;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Proves the outbox pattern end-to-end against REAL Postgres + REAL Kafka
// (not H2/embedded-kafka): RegistrationService writes AppUser+OutboxEvent in
// one DB tx, the scheduled OutboxRelay picks it up (2s poll), publishes to
// the real "user-events" topic, and a raw KafkaConsumer reads it back.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    // same image as the project's own compose.yaml (KRaft single-node)

    @Autowired
    RegistrationService registrationService;

    @Autowired
    OutboxRepository outboxRepository;

    KafkaConsumer<String, String> testConsumer;

    @BeforeEach
    void setUpConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of("user-events"));
    }

    @AfterEach
    void tearDownConsumer() {
        testConsumer.close();
    }

    @Test
    void givenNewRegistration_whenOutboxRelayRuns_thenEventIsPublishedToKafka() {
        // Given
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com");

        // When
        AppUser saved = registrationService.register(request);

        // Then — outbox row lands in the SAME tx as the user row
        assertThat(outboxRepository.findAll()).hasSize(1);

        // And — the scheduled relay (2s fixedDelay) eventually publishes it
        // and marks it processed once Kafka acks
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(outboxRepository.findAll().get(0).isProcessed()).isTrue());

        // And — a real consumer reading the real topic actually sees the message
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(500));
            List<ConsumerRecord<String, String>> matches = new ArrayList<>();
            records.forEach(matches::add);
            assertThat(matches)
                    .anyMatch(r -> r.key().equals(String.valueOf(saved.getId()))
                            && r.value().contains("\"username\":\"alice\""));
        });
    }
}
