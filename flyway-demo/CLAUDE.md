# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Flyway Demo** is a runnable Spring Boot 4 example demonstrating database migration management with Flyway. It's a companion project to liquibase-demo (in a sibling directory) showing equivalent functionality in two different migration tools.

**Core principle**: Migrations own the schema; Hibernate validates but never creates/alters tables (`ddl-auto=validate`).

**Stack**: Spring Boot 4.1.0 · Java 17 · H2 in-memory DB (dev) · PostgreSQL-ready · JPA/Hibernate

## Common Commands

```bash
# Build the project
./mvnw clean install

# Run all tests (includes end-to-end schema validation)
./mvnw test

# Run the app (boots on port 8080)
./mvnw spring-boot:run

# Run a single test
./mvnw test -Dtest=FlywayDemoApplicationTests#migrationsSeededData

# Build jar and run on custom port
./mvnw clean package
java -jar target/flyway-demo-0.0.1-SNAPSHOT.jar --server.port=8081

# View H2 web console while app runs
# App must be running (mvnw spring-boot:run)
# Visit: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:library;DB_CLOSE_DELAY=-1
```

## Architecture

### Directory Structure
```
src/main/
├── java/com/example/flywaydemo/
│   ├── FlywayDemoApplication.java    (Spring Boot entry)
│   ├── domain/                       (JPA entities + repos)
│   │   ├── Author.java               (entity; maps to author table)
│   │   ├── AuthorRepository.java      (Spring Data JPA)
│   │   ├── Book.java                 (entity; maps to book table)
│   │   └── BookRepository.java        (custom queries)
│   └── web/
│       └── LibraryController.java     (REST endpoints)
└── resources/
    └── application.properties         (DB/Flyway/JPA config)

src/test/
└── java/com/example/flywaydemo/
    └── FlywayDemoApplicationTests.java (end-to-end verification)
```

### Key Concepts

**Schema Design** — Migrations are versioned SQL files (V1..V4, plus R__ repeatable view):
- V1: Create `author` table
- V2: Create `book` table + FK to author + index
- V3: Alter `author` — add `email` column + unique constraint
- V4: Seed data (identity restart behavior)
- R__: Repeatable `book_catalog` view

The test verifies Flyway applied exactly 4 versioned migrations and the view is queryable.

**JPA Integration** — Entities live in `domain/` package with JPA annotations:
- `@Entity` + `@Table` map classes to schema
- Hibernate validates entities against Flyway-migrated schema at startup
- Never use `ddl-auto=create` or `update` — migrations own all DDL
- Repositories extend `JpaRepository` for basic CRUD + custom queries

**REST Endpoints** — `LibraryController` exposes:
- `GET  /api/authors`      — list all authors
- `GET  /api/books`        — list books with author join
- `POST /api/authors`      — create new author

### Flyway Configuration

File: `src/main/resources/application.properties`

Key settings:
- `spring.flyway.locations=classpath:db/migration` — where migrations live
- `spring.flyway.baseline-on-migrate=true` — adopt existing DBs (idempotent)
- `spring.flyway.validate-on-migrate=true` — fail if an applied migration was edited (checksum guard)
- `spring.datasource.url=jdbc:h2:mem:library;DB_CLOSE_DELAY=-1` — H2 in-memory, persisted for console access
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never touches schema

**PostgreSQL**: To switch to a real database, comment H2 block and uncomment PostgreSQL block in application.properties. No code changes needed; Flyway auto-detects dialect via `flyway-database-postgresql` dependency.

### Test Strategy

File: `src/test/java/com/example/flywaydemo/FlywayDemoApplicationTests.java`

Tests verify:
1. Context boots (Spring + Flyway + Hibernate all succeed)
2. Seed data exists (2 authors, 4 books from V4)
3. Flyway recorded exactly 4 versioned migrations in `flyway_schema_history`
4. Repeatable view `book_catalog` is queryable

**Why this approach**: If migrations are wrong, context fails to boot; if entities drift from schema, Hibernate rejects them. Tests prove end-to-end correctness without mocking the database.

## Important Notes

- **Never edit a migration that has already been applied** to a real database. H2 is recreated each boot, so you *can* edit during development; production DBs cannot.
- **ddl-auto=validate is non-negotiable** — it forces discipline: migrations own schema, app validates against it.
- **Flyway history table** (`flyway_schema_history`) is automatically created and maintained; inspect it to trace applied migrations.
- **H2 console** stays open during `spring-boot:run` because `DB_CLOSE_DELAY=-1`. Visit http://localhost:8080/h2-console to inspect the live H2 database.

## Git Workflow

- Main branch: `main`
- No special CI/CD; `./mvnw test` is the proof of correctness.
