# Kafka Streams from scratch — a runnable word-count walkthrough

A self-contained guide: scaffold a Spring Boot app, run a Kafka broker, create
topics, stream-count words, and watch it work. Copy-paste top to bottom.

---

## 0. Prerequisites (what the machine needs)

| Tool | Why | Check |
|---|---|---|
| Java 17+ | run the app | `java -version` |
| Maven (or the bundled `./mvnw`) | build | `mvn -version` |
| Spring CLI | `spring init` scaffolding | `spring --version` |
| A Docker daemon (Colima, Docker Desktop, …) | run the Kafka broker | `docker info` |

If you use **Colima** (lightweight Docker daemon for macOS):

```bash
colima start          # boots a small Linux VM that provides the docker daemon
docker info           # should now succeed
```

> Kafka Streams is *just a Java library*. It has no server of its own — it still
> needs a Kafka **broker** to read topics from and write topics to. That's what
> the Docker container below is for.

---

## 1. Scaffold the project with `spring init`

```bash
spring init \
  --build maven \
  --java-version 17 \
  --group-id com.example \
  --artifact-id kstreams-demo \
  --name kstreams-demo \
  --dependencies kafka,kafka-streams \
  kstreams-demo
```

Two dependencies do the work:
- **`kafka`** → Spring for Apache Kafka: plain producer/consumer, `KafkaTemplate`, `@KafkaListener`.
- **`kafka-streams`** → the streaming engine (`StreamsBuilder`, `KStream`, `KTable`).

(With current start.spring.io this resolves to Spring Boot 4.x / Java 17.)

---

## 2. Start a Kafka broker

One single-node broker in **KRaft mode** — no ZooKeeper, nothing to configure:

```bash
docker run -d --name kafka -p 9092:9092 apache/kafka:3.9.0
```

`-p 9092:9092` maps the broker's port to your laptop so the Spring app can reach
it at `localhost:9092`.

Wait until it's ready (the first call fails until the broker finishes booting):

```bash
until docker exec kafka /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 --list >/dev/null 2>&1; do
  echo "waiting for broker..."; sleep 1
done
echo "broker ready"
```

---

## 3. Create the topics  ← the part you asked about

A **topic** is a named, append-only log the broker keeps. Producers append to it;
consumers read from it. Our app uses two, so we create them explicitly:

```bash
# input: raw sentences go here
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic words-input \
  --partitions 1 --replication-factor 1

# output: (word -> running count) updates come out here
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic words-output \
  --partitions 1 --replication-factor 1
```

Flag by flag:
- `--bootstrap-server localhost:9092` — which broker to talk to.
- `--create --if-not-exists` — create it; don't error if it's already there.
- `--topic <name>` — the topic name.
- `--partitions 1` — split the log into N shards. 1 = simplest. More partitions =
  more parallelism, but ordering is only guaranteed *within* a partition, and a
  record's partition is chosen by its **key**. (For a demo, 1 keeps every word in
  order in one place.)
- `--replication-factor 1` — how many brokers hold a copy. We have one broker, so
  it must be 1. In production this is 3.

Confirm:

```bash
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
# -> words-input
#    words-output
```

> **Do you even have to create topics?** Often no — brokers can auto-create a
> topic on first use, and Kafka Streams auto-creates its own internal topics.
> But creating them explicitly means *you* control partitions/replication instead
> of taking the defaults, so it's the right habit.

---

## 4. Add the code (4 files)

### 4a. `src/main/resources/application.properties`
```properties
spring.kafka.bootstrap-servers=localhost:9092

# Kafka Streams app identity: names its consumer group AND prefixes its
# internal state-store/changelog topics.
spring.kafka.streams.application-id=wordcount-app
spring.kafka.streams.properties.default.key.serde=org.apache.kafka.common.serialization.Serdes$StringSerde
spring.kafka.streams.properties.default.value.serde=org.apache.kafka.common.serialization.Serdes$StringSerde
# Emit every count update immediately (nice for a demo; leave default in prod).
spring.kafka.streams.properties.statestore.cache.max.bytes=0
spring.kafka.streams.properties.commit.interval.ms=500

# Plain producer (feeds words-input)
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

# Plain consumer (prints words-output; values are Long counts)
spring.kafka.consumer.group-id=demo-printer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.LongDeserializer
```

### 4b. `WordCountTopology.java` — the streaming job
```java
@Configuration
@EnableKafkaStreams   // build a StreamsBuilder, wire it into the bean, auto-start on boot
public class WordCountTopology {

  @Bean
  public KStream<String, String> wordCountStream(StreamsBuilder builder) {
    KStream<String, String> lines = builder.stream("words-input");     // SOURCE
    lines
      .flatMapValues(line -> Arrays.asList(line.toLowerCase().split("\\W+"))) // line -> words
      .filter((k, word) -> !word.isBlank())
      .groupBy((k, word) -> word)                                      // RE-KEY by word
      .count(Materialized.as("counts-store"))                          // STATEFUL -> KTable
      .toStream()
      .to("words-output", Produced.with(Serdes.String(), Serdes.Long())); // SINK
    return lines;
  }
}
```

### 4c. `WordProducer.java` — feed some data on startup
```java
@Component
public class WordProducer implements CommandLineRunner {
  private final KafkaTemplate<String, String> template;
  public WordProducer(KafkaTemplate<String, String> template) { this.template = template; }

  @Override public void run(String... args) {
    List.of("the quick brown fox",
            "the lazy dog",
            "the quick fox jumps over the lazy dog")
        .forEach(line -> { template.send("words-input", line);
                           System.out.println("PRODUCED >> " + line); });
  }
}
```

### 4d. `CountConsumer.java` — print the results
```java
@Component
public class CountConsumer {
  @KafkaListener(topics = "words-output", groupId = "demo-printer")
  public void onCount(ConsumerRecord<String, Long> rec) {
    System.out.println("COUNT >> " + rec.key() + " = " + rec.value());
  }
}
```

---

## 5. Build & run

```bash
./mvnw -DskipTests package
java -jar target/kstreams-demo-0.0.1-SNAPSHOT.jar
```

(`-DskipTests` skips the generated context-load test, which would need a broker.)

You'll see the count climb live:
```
PRODUCED >> the quick brown fox
...
COUNT >> the = 1
COUNT >> the = 2
COUNT >> the = 3
COUNT >> the = 4
```

Stop with `Ctrl-C`.

---

## 6. Prove it's real (read the topic directly)

Independently of the app, ask the broker what's in `words-output`:

```bash
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic words-output --from-beginning --timeout-ms 4000 \
  --property print.key=true \
  --key-deserializer org.apache.kafka.common.serialization.StringDeserializer \
  --value-deserializer org.apache.kafka.common.serialization.LongDeserializer
```

Final tally: `the=4, quick=2, fox=2, lazy=2, dog=2, brown=1, jumps=1, over=1`.

---

## 7. Cleanup

```bash
docker rm -f kafka     # remove the broker container
colima stop            # (optional) shut the Docker VM down entirely
```

---

## How it actually works (the 5 ideas)

1. **KStream = an unbounded log of events.** `builder.stream("words-input")` is
   "every record that ever arrives on this topic," processed one at a time forever.

2. **Stateless operators** (`flatMapValues`, `filter`) transform each record with
   no memory of the past. Splitting a line into words needs nothing remembered.

3. **The key decides the partition, and partition decides co-location.**
   `groupBy(word)` moves the word into the record's *key*. Kafka routes equal keys
   to the same partition, so every occurrence of "fox" lands at the same counter.
   (Under the hood this writes to an internal "repartition" topic.)

4. **`count()` is stateful → produces a KTable.** It keeps a running total per key
   in a local **RocksDB state store**, and mirrors every change to an internal
   **changelog topic**. If the app crashes and restarts, it replays the changelog
   and the counts are intact. That's Kafka Streams' fault tolerance.

5. **A KTable is a changelog, not a snapshot.** Writing the table to `words-output`
   emits an *update per change* — which is why `the` came out as 1, 2, 3, 4 rather
   than just 4. **KStream = every event; KTable = latest value per key;** `count()`
   is the bridge. (`statestore.cache.max.bytes=0` made every update visible; the
   default batches them for throughput, so you'd see fewer, coalesced updates.)

---

## Follow-up ideas (each builds on this)

1. **Interactive types.** Change `words-input` values from `String` to a JSON
   object (e.g. `{ "user": "...", "text": "..." }`) using a JSON Serde. Learn how
   serialization boundaries work.

2. **Windowing** — counts *per time bucket*:
   ```java
   .groupBy((k, word) -> word)
   .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
   .count()
   ```
   Now you get "how many times per 10-second window," the basis of real-time metrics.

3. **Interactive Queries** — expose a REST endpoint that reads a word's current
   count straight from the local state store (`KafkaStreams#store(...)`), no output
   topic needed. Shows that the state store is itself a queryable database.

4. **Joins** — stream two topics (e.g. `orders` and `customers`) and join them on
   key to enrich events in real time. The single most useful streaming pattern.

5. **Scale out** — set `words-input` to 3 partitions, run 3 instances of the app
   with the same `application-id`, and watch Kafka rebalance partitions across them.
   Same code, horizontal scaling for free.

6. **Exactly-once** — set `processing.guarantee=exactly_once_v2` and learn how
   Kafka transactions make "read → process → write" atomic.
