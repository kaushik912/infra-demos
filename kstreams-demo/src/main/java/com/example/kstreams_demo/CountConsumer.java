package com.example.kstreams_demo;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the topology's output topic and prints each (word -> count) update.
 * Because caching is disabled, you'll see the count climb live as new
 * occurrences of a word arrive.
 */
@Component
public class CountConsumer {

	@KafkaListener(topics = "words-output", groupId = "demo-printer")
	public void onCount(ConsumerRecord<String, Long> record) {
		System.out.println("COUNT >> " + record.key() + " = " + record.value());
	}
}