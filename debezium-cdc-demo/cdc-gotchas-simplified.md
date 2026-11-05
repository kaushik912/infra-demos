# CDC Gotchas, Simplified

You change a row in Postgres. A few moments later, your Spring app logs it.
No polling, no triggers, no extra code in your CRUD layer. That's CDC
(Change Data Capture) with Debezium.

It works great. But a few things trip almost everyone up the first time.
Here they are, in plain words.

## The big picture

```
You (REST call)
   |
   v
Spring app :8080  --writes-->  Postgres
                                  |
                      (Postgres writes every change to its WAL log)
                                  |
                                  v
                     Debezium (inside Kafka Connect :8083)
                                  |
                                  v
                       Kafka topic: cdc.public.policy
                                  |
                                  v
                    Spring @KafkaListener  -->  logs the change
```

Notice there are **two** servers with REST APIs here:

- **:8080** is *your* Spring app. It has `/api/policies`.
- **:8083** is *Kafka Connect*. It has `/connectors`.

So if you can't find `/connectors` in your controller, that's why. It was never
yours. Connect ships with it.

## What does that `curl` actually do?

```bash
curl -s -X POST -H "Content-Type: application/json" \
  --data @connector/policy-connector.json \
  localhost:8083/connectors
```

It tells Kafka Connect: "Here's a job description. Please start a Postgres
connector with these settings."

Connect saves the job in Kafka, starts it, and from then on Debezium watches
the database for you. You only do this once.

Check on it anytime:

```bash
curl -s localhost:8083/connectors/policy-connector/status
```

You want to see `RUNNING`.

## Unraveling `policy-connector.json`

The file is just a name plus a bag of settings. Let's go through them in groups.

```json
{
  "name": "policy-connector",
  "config": { ... }
}
```

`name` is how you refer to the connector later (status, delete, etc.).

### 1. Which connector?

| Setting | Value | What it means |
|---|---|---|
| `connector.class` | `PostgresConnector` | "Use the Postgres flavor of Debezium." Other databases have their own class. |
| `plugin.name` | `pgoutput` | How Postgres formats the WAL for Debezium. `pgoutput` is built into Postgres 10+, so there's nothing to install. |

### 2. How to reach the database

| Setting | Value | What it means |
|---|---|---|
| `database.hostname` | `db` | Where Postgres is. |
| `database.port` | `5432` | Postgres's port *inside* Docker. |
| `database.user` / `password` | `postgres` / `postgres` | Demo credentials. Never do this for real. |
| `database.dbname` | `debezium` | Which database to watch. |

> **Gotcha:** `db` is the Docker Compose service name, and `5432` is the
> container's port. Your laptop uses `localhost:5433` for the same database.
> Debezium runs *inside* Docker, so it uses the inside address. Mixing these up
> is the #1 "connector can't connect" mistake.

### 3. What to watch

| Setting | Value | What it means |
|---|---|---|
| `table.include.list` | `public.policy` | Only watch this table. Format is `schema.table`. |
| `topic.prefix` | `cdc` | Start of every topic name. |

The topic name is built like this: `prefix.schema.table`, so you get
**`cdc.public.policy`**. That's the topic your `@KafkaListener` subscribes to.
Change the prefix and your listener hears nothing.

### 4. Postgres plumbing (slot and publication)

| Setting | Value | What it means |
|---|---|---|
| `slot.name` | `policy_slot` | A *replication slot*: Postgres's bookmark for "here's how far Debezium has read." |
| `publication.name` | `policy_pub` | A *publication*: the list of tables Postgres will stream. |
| `publication.autocreate.mode` | `filtered` | Debezium creates the publication for you, containing only the tables in `table.include.list`. |

The slot is the important one. Postgres will **not** throw away WAL log files
until the slot has read them. Which brings us to the scariest gotcha below.

### 5. What the messages look like

| Setting | Value | What it means |
|---|---|---|
| `key.converter` / `value.converter` | `JsonConverter` | Write messages as JSON. |
| `...schemas.enable` | `false` | Don't wrap every message in a big schema description. Just send the data. |
| `tombstones.on.delete` | `false` | After a delete event, don't send an extra empty "tombstone" message. |

With schemas off, each message is small and readable. This is what your
consumer parses:

```json
{ "before": null, "after": { "id": 1, "name": "..." }, "op": "c" }
```

`op` tells you what happened:

| op | Meaning |
|---|---|
| `c` | create (insert) |
| `u` | update |
| `d` | delete |
| `r` | read (the initial snapshot of existing rows) |

Tombstones are a Kafka trick for log compaction. We don't compact, so we turn
them off to keep the listener simple.

## The gotchas

### 1. Order matters

Do it in this order:

1. **Start the app first.** It creates the `policy` table (via JPA).
2. Run `ALTER TABLE policy REPLICA IDENTITY FULL;`
3. **Then** POST the connector.

If the table doesn't exist when the connector starts, there's nothing to watch.

### 2. Without `REPLICA IDENTITY FULL`, updates and deletes are thin

By default, Postgres only logs the primary key for the "old" version of a row.
So on an update or delete, `before` would be mostly empty. `REPLICA IDENTITY
FULL` tells Postgres to log the whole old row, so you get a proper
before-and-after.

### 3. Forgotten replication slots eat your disk

This is the big one. The slot says "I haven't read this WAL yet, keep it."
If you delete the connector but leave the slot, Postgres keeps every WAL file
forever. The disk slowly fills up.

Delete the connector **and** the slot when you're done:

```bash
curl -X DELETE localhost:8083/connectors/policy-connector
docker compose exec db psql -U postgres -d debezium \
  -c "SELECT pg_drop_replication_slot('policy_slot');"
```

Deleting the connector alone does not drop the slot.

### 4. Use `ddl-auto=update`, not `create-drop`

`create-drop` drops and recreates the table on every app restart. That breaks
the publication, which was tied to the old table. `update` leaves the table
alone.

### 5. `wal_level=logical` is not optional

Debezium reads the WAL, and Postgres only writes enough detail for that when
`wal_level=logical`. It's set in `compose.yaml`. On a managed database like
RDS, you flip this with a parameter (`rds.logical_replication=1`) and a reboot.

### 6. Two different Kafka addresses

Kafka is advertised twice in `compose.yaml`:

- `localhost:9092` for your Spring app on the host.
- `kafka:19092` for Connect, inside Docker.

Each client needs the address that works from where *it* is running.

## Quick cheat sheet

```bash
# 1. start infra
docker compose up -d

# 2. start the app (creates the table)
./mvnw spring-boot:run

# 3. full before/after on updates and deletes
docker compose exec db psql -U postgres -d debezium \
  -c "ALTER TABLE policy REPLICA IDENTITY FULL;"

# 4. register the connector
curl -s -X POST -H "Content-Type: application/json" \
  --data @connector/policy-connector.json localhost:8083/connectors

# 5. check it
curl -s localhost:8083/connectors/policy-connector/status

# 6. when done: delete connector AND slot
curl -X DELETE localhost:8083/connectors/policy-connector
docker compose exec db psql -U postgres -d debezium \
  -c "SELECT pg_drop_replication_slot('policy_slot');"
docker compose down -v
```

That's it. Once you see the pieces (Postgres writes the log, Debezium reads
it, Kafka carries it, your app listens), the rest is just settings.
