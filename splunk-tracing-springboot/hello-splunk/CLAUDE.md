# hello-splunk

Minimal Spring Boot app writing JSON logs to a file; `send-to-splunk.sh` tails it and ships to Splunk HEC. (Parent README: ../README.md)

## Stack
- Spring Boot 3.5.0, Java 17, Maven (`./mvnw` present); starters: web, actuator, test
- Shipper script needs bash, `jq`, `curl`, `tail`

## Commands
- Run: `./mvnw spring-boot:run` (port 8080, app name `hello-service`)
- Test: `./mvnw test` (no test sources in src/test currently)
- Ship logs: `SPLUNK_HEC_TOKEN=... ./send-to-splunk.sh`

## Env / external
- `SPLUNK_HEC_TOKEN` (required by script, never commit)
- Optional: `SPLUNK_HEC_URL` (default `https://localhost:8088/services/collector/event`), `LOG_FILE` (default `logs/hello-service.log`)
- Splunk with HEC enabled must be provided by user separately; script uses `curl -k` (self-signed OK). No Docker in project.

## Layout (com.example.hellosplunk)
- `HelloController`: GET /hello (info log), GET /hello/error (error log)
- `HelloSplunkApplication`
- Log file `logs/hello-service.log`, JSON pattern (time, level, logger, message) set in application.properties

## Gotchas
- Script uses `tail -n 0 -F`: only new lines shipped; start script before hitting endpoints
- No trace ids here (unlike ../../splunk-springboot-distributed)
