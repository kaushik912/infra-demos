# kstreams-demo

Kafka Streams word-count demo (Spring Boot). Reads `words-input`, counts words, writes `words-output` (String key, Long value).

## Stack
- Spring Boot 4.1.0, Java 17, Maven; spring-boot-starter-kafka + kafka-streams
- Maven wrapper: use `./mvnw`

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (KstreamsDemoApplicationTests only)

## Requires
- Kafka broker at `localhost:9092` (`spring.kafka.bootstrap-servers`), NOT started by project (no compose.yaml). GUIDE.md assumes Docker daemon (Colima etc.) to run broker; ask user before Docker.
- Topics `words-input`, `words-output` created manually per GUIDE.md (not auto-created in code)

## Layout (com.example.kstreams_demo)
- `WordCountTopology`: @EnableKafkaStreams, topology, state store `counts-store`
- `WordProducer`: CommandLineRunner, sends 3 sentences to words-input at startup
- `CountConsumer`: prints words-output (group `demo-printer`)

## Gotchas
- streams application-id `wordcount-app` names consumer group + changelog topics
- statestore cache 0, commit 500ms -> every count update emitted (demo only)
- Consumer uses LongDeserializer for values
- Docs: GUIDE.md, kafka_streams_java_working.md
