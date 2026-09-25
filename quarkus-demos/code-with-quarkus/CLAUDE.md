# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- Dev mode (live reload): `./mvnw quarkus:dev` — app on http://localhost:8080, Dev UI at http://localhost:8080/q/dev/
- Run all tests: `./mvnw test`
- Run a single test: `./mvnw test -Dtest=GreetingResourceTest`
- Package: `./mvnw package` → `target/quarkus-app/quarkus-run.jar`
- Über-jar: `./mvnw package -Dquarkus.package.jar.type=uber-jar`
- Native build: `./mvnw package -Dnative` (or `-Dquarkus.native.container-build=true` without GraalVM installed — this uses Docker, so confirm with the user first per their Docker policy)

## Architecture

Minimal Quarkus REST starter (`quarkus-arc` + `quarkus-rest`), Java 21.

- `src/main/java/org/acme/GreetingResource.java` — single JAX-RS resource, `GET /hello`
- `src/test/java/org/acme/GreetingResourceTest.java` — `@QuarkusTest`, runs against the app in the same JVM (dev/test mode)
- `src/test/java/org/acme/GreetingResourceIT.java` — `@QuarkusIntegrationTest`, runs against the packaged artifact (used by the `maven-failsafe-plugin` `integration-test`/`verify` goals, and the `native` Maven profile)
- `src/main/resources/application.properties` — empty; no config set yet
- `src/main/docker/` — Dockerfile variants (jvm, legacy-jar, native, native-micro) for container builds, not used by default dev/test workflow
