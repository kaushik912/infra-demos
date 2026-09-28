# service-b

Callee service in a two-service Splunk distributed-tracing demo (parent README: ../README.md). Logs JSON to console + Splunk HEC, correlated by traceId/spanId.

## Stack
- Spring Boot 3.3.4, Java 17, Maven (`./mvnw` present)
- actuator (health,info), micrometer-tracing-bridge-brave (sampling 1.0), logstash-logback-encoder 7.4, splunk-library-javalogging 1.11.11

## Commands
- Run: `./mvnw spring-boot:run` (port 8081)
- Test: `./mvnw test`

## Service link
Called by service-a at GET /hello (this service makes no outbound calls). Must listen on 8081.

## Env / external
- `SPLUNK_HEC_TOKEN` read in logback-spring.xml (default placeholder YOUR_HEC_TOKEN_HERE). Never hardcode.
- Splunk HEC expected at `https://localhost:8088` (hardcoded in logback-spring.xml; cert validation disabled), index `main`. Splunk run separately by user.

## Layout (com.example.serviceb)
- `HelloController`: GET /hello -> "Hello from B"
- `TraceIdResponseFilter`
- `ServiceBApplication`
- `resources/logback-spring.xml` (identical in service-a/b): CONSOLE (LogstashEncoder) + SPLUNK_HEC appenders

## Gotchas
- Tracing needs both services up; start service-b first
