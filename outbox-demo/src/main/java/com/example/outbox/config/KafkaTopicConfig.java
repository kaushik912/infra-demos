package com.example.outbox.config;

import com.example.outbox.outbox.OutboxRelay;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /** Auto-created on startup by KafkaAdmin. */
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(OutboxRelay.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}