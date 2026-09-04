package com.example.kstreams_demo;

import java.util.Arrays;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

/**
 * The whole streaming job. @EnableKafkaStreams makes Spring build a
 * StreamsBuilder, hand it to every KStream @Bean below, then start the
 * topology automatically when the app boots.
 */
@Configuration
@EnableKafkaStreams
public class WordCountTopology {

	@Bean
	public KStream<String, String> wordCountStream(StreamsBuilder builder) {
		// 1. SOURCE: read the input topic as an unbounded stream of records.
		//    key = null, value = a line of text.
		KStream<String, String> lines = builder.stream("words-input");

		lines
				// 2. STATELESS: split each line into individual words (one record -> many).
				.flatMapValues(line -> Arrays.asList(line.toLowerCase().split("\\W+")))
				.filter((key, word) -> !word.isBlank())

				// 3. RE-KEY: move the word into the KEY so records for the same word
				//    land in the same partition / same aggregation bucket.
				.groupBy((key, word) -> word)

				// 4. STATEFUL: count per key. This builds a KTable backed by a local
				//    RocksDB state store ("counts-store") + a changelog topic for fault tolerance.
				.count(Materialized.as("counts-store"))

				// 5. SINK: turn the changing table back into a stream of updates and
				//    write (word -> count) to the output topic.
				.toStream()
				.to("words-output", Produced.with(Serdes.String(), Serdes.Long()));

		return lines;
	}
}