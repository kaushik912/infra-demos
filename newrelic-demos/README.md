# newrelic-demos

## What this is

A small Spring Boot app built to try out **New Relic APM as a debugging
tool** on a local/personal project. New Relic isn't open source but its free
tier (100 GB/mo ingest, 1 full platform user, no credit card) is generous
enough for this.

The app exposes four endpoints, each deliberately built to trigger a
different New Relic debugging view — a slow transaction, an unhandled
exception, a chatty DB query, and an outbound HTTP call. Attach the New Relic
Java agent, hit the endpoints, and watch each one show up in the dashboard.

Stack: Spring Boot 4 (Java 21, Maven), H2 in-memory DB (no Docker needed),
Swagger UI for exploring the endpoints.

## Setup

### 1. Get a license key

1. Sign up at https://newrelic.com (free tier, no card).
2. Account settings → copy your **License Key** (ingest key, e.g. starts
   with `NRAK-` or is a plain 40-char string).
3. Export it — don't hardcode it anywhere in the repo:
   ```bash
   export NEW_RELIC_LICENSE_KEY=your_key_here
   ```

### 2. Download the Java agent

```bash
mkdir -p agent
curl -o agent/newrelic.zip https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip
unzip agent/newrelic.zip -d agent
```
This drops `agent/newrelic/newrelic.jar` plus a default `newrelic.yml`. Both
`agent/` and the license key are gitignored — never commit either.

### 3. Build and run with the agent attached

```bash
./mvnw -q -DskipTests package
java -javaagent:agent/newrelic/newrelic.jar \
     -Dnewrelic.config.license_key=$NEW_RELIC_LICENSE_KEY \
     -Dnewrelic.config.app_name="newrelic-demos" \
     -jar target/newrelic-demos-0.0.1-SNAPSHOT.jar
```

App comes up on `http://localhost:8080`. Swagger UI at
`http://localhost:8080/swagger-ui.html`. First request creates the app entry
under **APM & Services → newrelic-demos** in the New Relic dashboard —
usually visible within ~1 minute.

Running without `-javaagent` also works (e.g. for a quick local check) — the
app just won't report anything; `newrelic-api` calls are no-ops when the
agent isn't attached.

## Testing each scenario

For each one: send the request, then go check the matching view in New
Relic (**APM & Services → newrelic-demos**).

### 1. Slow transaction trace

```bash
curl "localhost:8080/api/debug/slow?millis=3000"
```
- **Where to look:** Transactions tab → find `DebugController/slow` → open
  a trace. It'll show a waterfall with the sleep taking up almost the whole
  transaction.
- **What to notice:** the custom attribute `demo.slow.millis` on the trace —
  useful for filtering/searching slow traces by how slow they were.
- Try a few different `millis` values (e.g. `200` vs `5000`) to see the
  apdex/response-time graph move.

### 2. Error tracking

```bash
curl -i localhost:8080/api/debug/error
```
- **Where to look:** Errors inbox (left nav) → an entry for
  `IllegalStateException: Simulated failure for New Relic error tracking demo`.
- **What to notice:** full stack trace, the transaction it happened in, and
  that it was reported twice over — once automatically (agent catches the
  unhandled exception) and once explicitly via the `NewRelic.noticeError`
  call in the controller. Good example of manual vs. automatic error
  capture.

### 3. Database query span

```bash
curl "localhost:8080/api/debug/db-query?fragment=widget"
```
- **Where to look:** Databases tab, or open the transaction trace for
  `DebugController/dbQuery` and expand the segments.
- **What to notice:** instead of one query, you'll see the initial `LIKE`
  search plus a separate `SELECT` per matched row (a deliberate N+1) — this
  is the shape of query you'd actually want New Relic to flag in a real app.
- Change `fragment` (e.g. `gear` vs `widget`) to vary how many rows come
  back and how many extra queries fire.

### 4. External call / distributed tracing

```bash
curl localhost:8080/api/debug/external-call
```
- **Where to look:** open the transaction trace for
  `DebugController/externalCall` → an external segment for
  `httpbin.org`. Also check the "External services" summary on the app
  overview page.
- **What to notice:** the agent auto-instruments the outbound `RestClient`
  call — no code changes needed beyond making the HTTP call itself.

### Generating a steady stream of data

Dashboards look more useful with continuous traffic rather than one-off
requests:
```bash
while true; do
  curl -s localhost:8080/api/debug/slow >/dev/null
  curl -s localhost:8080/api/debug/db-query >/dev/null
  curl -s localhost:8080/api/debug/external-call >/dev/null
  curl -s localhost:8080/api/debug/error >/dev/null
  sleep 2
done
```

## Reference: endpoints

| Endpoint | New Relic view it lights up |
|---|---|
| `GET /api/debug/slow?millis=1500` | Transactions → slow trace |
| `GET /api/debug/error` | Errors inbox |
| `GET /api/debug/db-query?fragment=widget` | Databases view / DB spans in trace |
| `GET /api/debug/external-call` | External services / distributed tracing |

## Notes

- H2 in-memory DB, seeded with 5 rows on startup — no external DB/Docker
  needed.
- Swagger UI (springdoc) documents all endpoints — driven by `@Tag` /
  `@Operation` annotations on `DebugController`.
