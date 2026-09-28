# service-a

Caller service in a two-service Splunk distributed-tracing demo (parent README: ../README.md). Logs JSON to console + Splunk HEC, correlated by traceId/spanId.

## Stack
- Spring Boot 3.3.4, Java 17, Maven (`./mvnw` present)
- actuator (health,info), micrometer-tracing-bridge-brave (sampling 1.0), logstash-logback-encoder 7.4, splunk-library-javalogging 1.11.11

## Commands
- Run: `./mvnw spring-boot:run` (port 8080)
- Test: `./mvnw test`

## Service link
GET /call-b (CallBController) -> RestTemplate (from RestTemplateBuilder, propagates trace headers) -> `http://localhost:8081/hello` (URL hardcoded).

## Env / external
- `SPLUNK_HEC_TOKEN` read in logback-spring.xml (default placeholder YOUR_HEC_TOKEN_HERE). Never hardcode.
- Splunk HEC expected at `https://localhost:8088` (hardcoded in logback-spring.xml; cert validation disabled), index `main`. Splunk run separately by user.

## Layout (com.example.servicea)
- `CallBController`: /call-b
- `TraceIdResponseFilter`: exposes traceId in response (Tracer)
- `ServiceAApplication`
- `resources/logback-spring.xml` (identical in service-a/b): CONSOLE (LogstashEncoder) + SPLUNK_HEC appenders

## Gotchas
- Tracing needs both services up; start service-b first
