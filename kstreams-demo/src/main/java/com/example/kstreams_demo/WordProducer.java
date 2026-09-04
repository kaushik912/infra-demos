package com.example.kstreams_demo;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * On startup, publish a few sentences to words-input so the topology has
 * something to chew on. In real life another service would produce these.
 */
@Component
public class WordProducer implements CommandLineRunner {

	private final KafkaTemplate<String, String> template;

	public WordProducer(KafkaTemplate<String, String> template) {
		this.template = template;
	}

	@Override
	public void run(String... args) {
		List<String> sentences = List.of(
				"the quick brown fox",
				"the lazy dog",
				"the quick fox jumps over the lazy dog");

		sentences.forEach(line -> {
			template.send("words-input", line);
			System.out.println("PRODUCED >> " + line);
		});
	}
}