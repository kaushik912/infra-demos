# debezium-cdc-demo

Postgres WAL -> Debezium (Kafka Connect) -> Kafka -> Spring Boot consumer.
The app writes to `policy` via REST and **never touches Kafka on the write path**. Debezium reads the WAL and publishes.

```
REST CRUD -> Postgres (wal_level=logical) -> Debezium Connect -> Kafka topic cdc.public.policy -> @KafkaListener (logs)
```

Compared to `outbox-demo` (polling relay): no scheduler, no outbox table, near real-time, but needs Connect and a replication slot.

## Run (needs Docker)

```bash
# 1. infra
docker compose up -d

# 2. app FIRST (creates table `policy` via ddl-auto=update)
./mvnw spring-boot:run

# 3. so UPDATE/DELETE events carry the "before" row
docker compose exec db psql -U postgres -d debezium -c "ALTER TABLE policy REPLICA IDENTITY FULL;"

# 4. register connector (table must exist; waits until Connect is up)
curl -s -X POST -H "Content-Type: application/json" \
  --data @connector/policy-connector.json http://localhost:8083/connectors
curl -s http://localhost:8083/connectors/policy-connector/status
```

## Try it

```bash
curl -s -X POST localhost:8080/api/policies -H 'Content-Type: application/json' \
  -d '{"policyNumber":"POL-001","policyHolder":"Ann"}'
curl -s -X PUT localhost:8080/api/policies/1 -H 'Content-Type: application/json' \
  -d '{"policyNumber":"POL-001","policyHolder":"Bob"}'
curl -s -X DELETE localhost:8080/api/policies/1
```

App log shows `INSERT ...`, `UPDATE ... -> ...`, `DELETE ...`.

Raw events:
```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic cdc.public.policy --from-beginning
```

## Inspect the WAL side

```bash
docker compose exec db psql -U postgres -d debezium -c \
  "SELECT slot_name, plugin, active, restart_lsn, confirmed_flush_lsn FROM pg_replication_slots;"
docker compose exec db psql -U postgres -d debezium -c "SELECT * FROM pg_publication_tables;"
```

## Teardown

```bash
curl -X DELETE http://localhost:8083/connectors/policy-connector   # also drop the slot, or WAL piles up
docker compose down -v
```

## Event shape (schemas disabled)

`{"before":{...}|null, "after":{...}|null, "source":{...,"lsn":...}, "op":"c|u|d|r", "ts_ms":...}`
Timestamps (`start_date`) arrive as epoch **microseconds**.

## Gotchas

- Slot keeps WAL while Connect is down -> disk fills. Monitor `pg_replication_slots`.
- Without `REPLICA IDENTITY FULL`, `before` is null on UPDATE (and only PK on DELETE).
- `ddl-auto=create-drop` would drop the table + break the publication. Use `update`.
- Delivery is at-least-once: consumer must be idempotent.
- Connector JSON: `topic.prefix` is required in Debezium 2.x (old `database.server.name` is gone).
