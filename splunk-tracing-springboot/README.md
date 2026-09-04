# Spring Boot Hello World with Splunk

This tutorial creates a small Spring Boot application that exposes `/hello`, writes structured JSON logs, and sends those logs to local Splunk through the **HTTP Event Collector**, or HEC. HEC uses token authentication and avoids installing a Splunk forwarder in the application container. [docs.splunk](https://docs.splunk.com/Documentation/Splunk/9.4.2/Data/UsetheHTTPEventCollector)

## Architecture

```text
curl → Spring Boot /hello → JSON application logs → Splunk HEC → Splunk search
```

You will run:

- Spring Boot on `localhost:8080`.
- Splunk Enterprise in Docker on `localhost:8000`.
- Splunk HEC on `localhost:8088`.

## 1. Start Splunk

```bash
docker run -d \
  --name splunk \
  -p 8000:8000 \
  -p 8088:8088 \
  -p 8089:8089 \
  -e "SPLUNK_START_ARGS=--accept-license" \
  -e "SPLUNK_GENERAL_TERMS=--accept-sgt-current-at-splunk-com" \
  -e "SPLUNK_PASSWORD=ChangeMe123!" \
  splunk/splunk:latest
```

Check startup:

```bash
docker logs -f splunk
```

Open [Splunk Web](http://localhost:8000) and log in:

```text
Username: admin
Password: ChangeMe123!
```

Wait until Splunk finishes initializing.

## 2. Create a HEC token

In Splunk Web:

1. Open **Settings → Data Inputs**.
2. Select **HTTP Event Collector**.
3. Select **New Token**.
4. Enter the name:

```text
spring-hello-token
```

5. Select **Next**.
6. Leave the source type as **Automatic**. (If you hit issues, try setting it explicitly to `spring_boot_json` for troubleshooting — this is what worked in testing.)
7. Select the `main` index for this simple example.
8. Select **Review → Submit**.
9. Copy the generated token.

HEC expects the token in an HTTP header using the format `Authorization: Splunk <token>`. [docs.splunk](https://docs.splunk.com/Documentation/Splunk/9.4.2/Data/UsetheHTTPEventCollector)

You can test HEC directly before writing any Java code:

```bash
export SPLUNK_HEC_TOKEN='paste-your-token-here'

curl -k https://localhost:8088/services/collector/event \
  -H "Authorization: Splunk ${SPLUNK_HEC_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "sourcetype": "spring_boot_json",
    "event": {
      "message": "Hello from curl",
      "application": "hello-service",
      "environment": "local"
    }
  }'
```

A successful response normally contains a zero status code, for example:

```json
{"text":"Success","code":0}
```

Search for it:

```spl
index=main sourcetype=spring_boot_json "Hello from curl"
```

## 3. Generate the Spring Boot project

Create a Maven project with:

- Java 17 or later.
- Spring Web.
- Spring Boot Actuator.
- Spring Boot DevTools, optional.

A minimal `pom.xml` is:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
           http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>hello-splunk</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Spring Boot supports structured JSON logging, including Logstash-compatible JSON output, through its logging configuration. [docs.spring](https://docs.spring.io/spring-boot/reference/features/logging.html)

## 4. Add the application

Create:

```text
src/main/java/com/example/hellosplunk/HelloSplunkApplication.java
```

```java
package com.example.hellosplunk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelloSplunkApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloSplunkApplication.class, args);
    }
}
```

Create:

```text
src/main/java/com/example/hellosplunk/HelloController.java
```

```java
package com.example.hellosplunk;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private static final Logger log =
            LoggerFactory.getLogger(HelloController.class);

    @GetMapping("/hello")
    public Map<String, Object> hello(
            @RequestParam(defaultValue = "World") String name) {

        String requestId = UUID.randomUUID().toString();

        log.info(
                "hello request requestId={} name={} timestamp={}",
                requestId,
                name,
                Instant.now()
        );

        return Map.of(
                "message", "Hello, " + name + "!",
                "requestId", requestId
        );
    }

    @GetMapping("/hello/error")
    public Map<String, String> error() {
        String requestId = UUID.randomUUID().toString();

        log.error(
                "simulated application error requestId={} errorType={}",
                requestId,
                "DemoException"
        );

        return Map.of(
                "message", "An error was logged",
                "requestId", requestId
        );
    }
}
```

Run the application:

```bash
./mvnw spring-boot:run
```

Test it:

```bash
curl "http://localhost:8080/hello?name=Alice"
```

Expected response:

```json
{
  "message": "Hello, Alice!",
  "requestId": "..."
}
```

Generate an error event:

```bash
curl http://localhost:8080/hello/error
```

## 5. Send logs to Splunk with HEC

The simplest local approach is:

1. Spring Boot writes logs to a file.
2. A small sidecar script reads new lines.
3. The script sends each line to Splunk HEC.

This keeps the application independent from Splunk-specific Java libraries.

Configure file logging in:

```text
src/main/resources/application.properties
```

```properties
spring.application.name=hello-service
server.port=8080

logging.file.name=logs/hello-service.log
logging.pattern.file={"time":"%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}","level":"%level","logger":"%logger{36}","message":"%msg"}%n
```

Start the application again:

```bash
mkdir -p logs
./mvnw spring-boot:run
```

Generate events:

```bash
curl "http://localhost:8080/hello?name=Alice"
curl "http://localhost:8080/hello?name=Bob"
curl http://localhost:8080/hello/error
```

Create `send-to-splunk.sh`:

```bash
#!/usr/bin/env bash

set -euo pipefail

SPLUNK_HEC_URL="${SPLUNK_HEC_URL:-https://localhost:8088/services/collector/event}"
SPLUNK_HEC_TOKEN="${SPLUNK_HEC_TOKEN:?Set SPLUNK_HEC_TOKEN first}"
LOG_FILE="${LOG_FILE:-logs/hello-service.log}"

tail -n 0 -F "$LOG_FILE" | while IFS= read -r line; do
  event=$(jq -cn \
    --arg sourcetype "spring_boot_json" \
    --arg source "$LOG_FILE" \
    --arg application "hello-service" \
    --arg environment "local" \
    --arg raw "$line" \
    '{
      sourcetype: $sourcetype,
      source: $source,
      event: {
        application: $application,
        environment: $environment,
        raw: $raw
      }
    }')

  curl -sk "$SPLUNK_HEC_URL" \
    -H "Authorization: Splunk ${SPLUNK_HEC_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$event" \
    >/dev/null
done
```

Install `jq` if required:

```bash
sudo apt-get update
sudo apt-get install -y jq
```

Make the script executable:

```bash
chmod +x send-to-splunk.sh
```

Export the token:

```bash
export SPLUNK_HEC_TOKEN='paste-your-token-here'
```

Start the log shipper:

```bash
./send-to-splunk.sh
```

In another terminal, generate logs:

```bash
curl "http://localhost:8080/hello?name=Charlie"
curl http://localhost:8080/hello/error
```

HEC supports sending either raw text or JSON event payloads; this example uses JSON because it is easier to extend with application, environment, and request metadata. [help.splunk](https://help.splunk.com/en/splunk-enterprise/get-data-in/collect-http-event-data/http-event-collector-examples)

## 6. Search the Spring Boot logs

Search all application events:

```spl
index=main sourcetype=spring_boot_json
```

Show the raw log message:

```spl
index=main sourcetype=spring_boot_json
| table _time application environment raw
```

Find successful requests:

```spl
index=main sourcetype=spring_boot_json "hello request"
```

Find error events:

```spl
index=main sourcetype=spring_boot_json "simulated application error"
```

Count events by application:

```spl
index=main sourcetype=spring_boot_json
| stats count by application
```

Count by environment:

```spl
index=main sourcetype=spring_boot_json
| stats count by environment
```

Extract the request ID:

```spl
index=main sourcetype=spring_boot_json
| rex field=raw "requestId=(?<requestId>[a-f0-9-]+)"
| table _time requestId raw
```

Search one request across events:

```spl
index=main sourcetype=spring_boot_json
| rex field=raw "requestId=(?<requestId>[a-f0-9-]+)"
| search requestId="<request-id>"
```

## 7. Better JSON event forwarding

The previous script wraps the entire application log line in `raw`. For production-style searches, send structured fields directly.

Replace the `event=$(...)` block with:

```bash
event=$(jq -cn \
  --arg sourcetype "spring_boot_json" \
  --arg source "$LOG_FILE" \
  --arg raw "$line" \
  '{
    sourcetype: $sourcetype,
    source: $source,
    event: {
      raw: $raw
    }
  }')
```

For a Spring Boot application, a more scalable production setup is usually:

```text
Spring Boot JSON logs → Fluent Bit / OpenTelemetry Collector → Splunk HEC
```

For this hello-world example, the shell shipper is easier to understand and debug. Spring Boot’s built-in structured logging support can produce machine-readable JSON directly, reducing the need for regular-expression extraction later. [docs.spring](https://docs.spring.io/spring-boot/reference/features/logging.html)

## 8. Troubleshooting checklist

Check that HEC is listening:

```bash
curl -k https://localhost:8088/services/collector/health
```

Check the Splunk container ports:

```bash
docker ps
```

Check whether the application is writing logs:

```bash
tail -f logs/hello-service.log
```

Check the shipper process:

```bash
ps aux | grep send-to-splunk
```

Use a broad Splunk query first:

```spl
index=* earliest=-15m "hello-service"
```

Then narrow it:

```spl
index=main sourcetype=spring_boot_json application=hello-service
```

For local development, `-k` and `-s` are convenient because the Docker instance commonly uses a self-signed certificate. Do not use `-k` in production; configure a trusted certificate instead.

## 9. Graceful shutdown

Stop the pieces in reverse order: shipper, then Spring Boot, then Splunk.

Stop the log shipper:

```bash
pgrep -af send-to-splunk.sh
kill <pid>
```

Stop Spring Boot:

```bash
pgrep -af spring-boot:run
kill <pid>
```

Or, if running in the foreground, `Ctrl+C`.

Stop Splunk, keeping the container so `docker start splunk` picks up where it left off:

```bash
docker stop splunk
```

Remove the container entirely, only if you want a clean slate next time:

```bash
docker rm -f splunk
```