# Transactional Outbox Pattern — Spring Boot demo

A minimal, runnable example of the **transactional outbox** pattern.

`POST /register` writes a **user row** and an **outbox row** in the *same* DB
transaction. A scheduled **relay** later reads unprocessed outbox rows, publishes
them to **Kafka**, and marks them processed. A **consumer** subscribes to the
topic to prove the fan-out.

## Why

Writing to a DB and publishing to Kafka aren't atomic — a crash between the two
either loses the event or double-commits it. The outbox makes the DB the single
source of truth: business row + "intent to publish" row commit together, so a
separate relay can publish safely with no dual-write race.

## Flow

```
POST /register
    │
    ▼
RegistrationService.register()   [@Transactional]
    ├─ save AppUser
    └─ save OutboxEvent (processed=false)      ← same DB tx, commits atomically
                │
                ▼ (every 2s)
       OutboxRelay.publishPending()  [@Scheduled, @Transactional]
                ├─ fetch unprocessed batch (oldest first)
                ├─ send to Kafka, block for ack
                └─ mark row processed            ← only after Kafka ack
                                │
                                ▼
                  UserEventConsumer (@KafkaListener) logs the message
```

## Key pieces

| Class | Role |
|---|---|
| `AppUser` / `UserRepository` | business aggregate being registered |
| `OutboxEvent` / `OutboxRepository` | one row per event awaiting publish; indexed on `(processed, id)` |
| `RegistrationService` | writes user + outbox row in one transaction |
| `OutboxRelay` | polls every 2s, publishes to Kafka, marks processed after ack |
| `KafkaTopicConfig` | auto-creates the `user-events` topic |
| `UserEventConsumer` | stand-in downstream subscriber (logs only) |

## Guarantees & tradeoffs

- **At-least-once delivery**: if the app crashes after Kafka ack but before the
  DB commit, the event resends next tick. Consumers must be idempotent.
- **Per-aggregate ordering**: Kafka message key = `aggregateId` (the user id), so
  a user's events land in one partition, in order. A failed send halts the batch
  rather than skipping ahead and reordering.
- **No 2PC, no dual-write race** — at the cost of publish latency (up to the
  poll interval) and a polling relay to run/monitor.

## Run

```bash
./mvnw spring-boot:run
```

Spring Boot's docker-compose support auto-starts Kafka from `compose.yaml`
(KRaft mode, single node) and wires `bootstrap-servers` for you.

```bash
curl -X POST localhost:8080/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com"}'
```

Watch the logs: `[relay] published outbox id=...` followed by
`[consumer] >>> received from Kafka: ...`.

H2 console: http://localhost:8080/h2-console (`jdbc:h2:mem:testdb`, in-memory,
resets on restart).

## Stack

Spring Boot 4.1 (WebMVC, Data JPA, Kafka, Validation) · H2 · Kafka 3.9 (KRaft) ·
Java 17
