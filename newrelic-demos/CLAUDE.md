# newrelic-demos

Spring Boot app with 4 deliberately-instrumented endpoints to try New Relic APM debugging views.

## Stack
- Spring Boot 4.1.1, Java 21, Maven (`./mvnw` present)
- webmvc, data-jpa, H2 in-mem (`jdbc:h2:mem:newrelic-demos`), springdoc-openapi 2.8.6, newrelic-api 8.21.0

## Commands
- Build: `./mvnw -q -DskipTests package`
- Run plain: `./mvnw spring-boot:run` (agent-less works; newrelic-api calls are no-ops)
- Run w/ agent: `java -javaagent:agent/newrelic/newrelic.jar -Dnewrelic.config.license_key=$NEW_RELIC_LICENSE_KEY -Dnewrelic.config.app_name="newrelic-demos" -jar target/newrelic-demos-0.0.1-SNAPSHOT.jar`
- Test: `./mvnw test`
- Port 8080; Swagger `/swagger-ui.html`; H2 console enabled

## Env / external
- `NEW_RELIC_LICENSE_KEY` (export; never commit). Needs New Relic account (free tier) only for reporting.
- Agent jar in `agent/` (gitignored; download steps in README). No Docker.

## Layout (com.demo.newrelic)
- `debug/DebugController`: GET /api/debug/slow?millis=, /error, /db-query?fragment=, /external-call (httpbin.org/delay/1, needs internet)
- `debug/GlobalExceptionHandler`
- `widget/`: Widget, WidgetRepository, WidgetSeeder (seed data for db-query)

## Gotchas
- /error reported twice: agent auto + explicit `NewRelic.noticeError`
- Custom attribute `demo.slow.millis` on slow trace
