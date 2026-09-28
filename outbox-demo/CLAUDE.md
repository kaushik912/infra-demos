# outbox-demo

Transactional outbox pattern: POST /register writes user + outbox row in one tx; scheduled relay publishes to Kafka, marks processed after ack; consumer logs.

## Stack
- Spring Boot 4.1.0, Java 17, Maven; webmvc, data-jpa, validation, kafka, H2 (runtime), postgresql (tests)
- Test libs: Testcontainers (postgresql, kafka), RestAssured 5.5.1, Awaitility 4.3.0
- Maven wrapper: use `./mvnw`

## Commands
- Run: `./mvnw spring-boot:run` (port 8080; H2 console `/h2-console`, `jdbc:h2:mem:testdb`)
- Test: `./mvnw test`
- Try: `POST /register` body {username, email}

## Requires Docker
- Run: spring-boot-docker-compose auto-starts Kafka from `compose.yaml` (apache/kafka 3.9.1, KRaft, single node, 9092) and wires bootstrap-servers. Fallback: manual Kafka at localhost:9092.
- Tests: Testcontainers spin real Postgres + Kafka -> Docker must run. Ask user before starting Docker.

## Layout (com.example.outbox)
- `registration/`: RegisterController, RegistrationService (@Transactional), RegisterRequest
- `user/`: AppUser, UserRepository
- `outbox/`: OutboxEvent, OutboxRepository, OutboxRelay (@Scheduled every 2s)
- `consumer/UserEventConsumer` (@KafkaListener); `config/KafkaTopicConfig` creates `user-events`
- Tests: OutboxIntegrationTest, RegisterControllerRestAssuredTest (201, 409 dup username)

## Gotchas
- At-least-once: consumers must be idempotent. Key = aggregateId for per-user ordering; failed send halts batch.
- H2 in-mem resets on restart (`ddl-auto=update`)
