# debezium-cdc-demo

Log-based CDC: REST CRUD on `policy` (Postgres) -> Debezium Connect reads WAL -> Kafka `cdc.public.policy` -> `@KafkaListener` logs. Modeled on hendisantika/spring-boot-postgres-debezium, cleaned up.

## Stack
- Spring Boot 4.1.0, Java 17, Maven; data-jpa, webmvc, kafka, postgresql. No Lombok.
- Infra (compose.yaml): postgres:17 (`wal_level=logical`, host 5433), apache/kafka 3.9.1 KRaft (host 9092, containers 19092), quay.io/debezium/connect 2.7.3 (8083)

## Commands
- Unit test (no Docker): `./mvnw test` (PolicyChangeConsumerTest, envelope parsing only)
- Run: `./mvnw spring-boot:run` -> :8080. Full flow in README.md

## Requires Docker
- `docker compose up -d` is manual; app does NOT auto-start it. Ask user before starting Docker.
- Order matters: app first (creates table) -> `ALTER TABLE policy REPLICA IDENTITY FULL` -> POST `connector/policy-connector.json` to :8083.

## Layout (com.example.cdc)
- `policy/`: Policy, PolicyRepository, PolicyController (CRUD)
- `consumer/PolicyChangeConsumer`: parses Debezium envelope (op c/u/d/r)
- `connector/policy-connector.json`: connector config (pgoutput, slot `policy_slot`, schemas disabled)

## Gotchas
- Orphan replication slot bloats WAL: delete connector + slot on teardown.
- `ddl-auto=update` on purpose (create-drop breaks publication).
