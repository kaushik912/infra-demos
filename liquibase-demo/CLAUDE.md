# liquibase-demo

Library app (authors/books) where Liquibase YAML changelogs own the schema; Hibernate only validates.

## Stack
- Spring Boot 4.1.0, Java 17, Maven; webmvc, data-jpa, liquibase, H2 (default), postgresql driver
- Maven wrapper: use `./mvnw`

## Commands
- Run: `./mvnw spring-boot:run` -> http://localhost:8080
- Test: `./mvnw test` (LiquibaseDemoApplicationTests)

## Requires
- Nothing external: H2 in-mem `jdbc:h2:mem:library`, user `sa`, no password. Console `/h2-console`.
- Postgres: swap datasource block in application.properties (commented example there); changelogs unchanged.

## Layout (com.example.liquibasedemo)
- `domain/`: Author, Book, repositories
- `web/LibraryController`: GET /api/authors, GET /api/books, POST /api/authors
- `resources/db/changelog/`: `db.changelog-master.yaml` + `changes/v1..v5__*.yaml`

## Gotchas
- `ddl-auto=validate`: entity changes need a new changeset, never edit applied ones
- Seed data (v4) only runs with `spring.liquibase.contexts=dev` (set)
- v5 view changeset `runOnChange: true`; v3 has columnExists precondition (MARK_RAN)
- Master changelog asserts DB engine h2/postgresql
