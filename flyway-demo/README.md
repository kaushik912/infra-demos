# Spring Boot database migration — Flyway vs Liquibase

Two **runnable, end-to-end** Spring Boot 4 projects that build the *exact same*
"library" schema (authors → books, one-to-many) and expose the *exact same* REST
API — one using **Flyway**, one using **Liquibase**. Compare them side by side.

```
spring-db-migration-examples/
├── flyway-demo/      # schema owned by SQL migrations (V1..V4 + R__ repeatable)
└── liquibase-demo/   # schema owned by YAML changelogs (v1..v5 changesets)
```

Both: Spring Boot 4.1.0 · Java 17 · H2 in-memory (zero external setup) · JPA in
`ddl-auto=validate` mode · Postgres config included (commented) for real use.

## The one idea both tools share

**Your application never creates or alters tables.** A migration tool applies an
ordered, checksummed, recorded history of schema changes at startup; Hibernate is
set to `validate` so it only checks the entities match. This gives you:

- a schema that is **versioned in git** alongside code,
- **repeatable, identical** environments (dev = CI = prod),
- an **audit trail** of every change (a history table the tool maintains),
- **fail-fast** safety if someone edits an already-applied change.

The golden rule for both: **never edit a migration that has already run** — add a
new one. (These demos use in-memory H2 recreated each boot, so you *can* edit them
freely while learning; a real database cannot.)

## Run either one

```bash
cd flyway-demo    && ./mvnw test && ./mvnw spring-boot:run   # port 8080
# or
cd liquibase-demo && ./mvnw test && ./mvnw spring-boot:run   # port 8080

curl localhost:8080/api/books
curl localhost:8080/api/authors
curl -X POST localhost:8080/api/authors -H 'Content-Type: application/json' \
     -d '{"name":"Octavia E. Butler","email":"octavia@example.com"}'
```

`./mvnw test` is itself the end-to-end proof: the Spring context boots → the
migration tool runs against H2 → Hibernate validates the entities against the
produced schema → tests assert the seed data and the history table.

> Note: `spring-boot:run` uses port 8080. If it's taken, run the jar on another
> port instead: `./mvnw package && java -jar target/*.jar --server.port=8081`.

## Side-by-side

| Concern | Flyway | Liquibase |
|---|---|---|
| Change format | **SQL files** (`V1__x.sql`) | XML / **YAML** / JSON / SQL changesets |
| Database portability | you write dialect SQL | **declared change types** emit per-DB SQL |
| Naming / ordering | `V<version>__desc.sql`, filename = order | `(id, author)` pair, order = master include order |
| History table | `flyway_schema_history` | `DATABASECHANGELOG` (+ `...LOCK`) |
| Re-runnable object (view/proc) | **Repeatable** `R__x.sql` (checksum) | `runOnChange: true` changeset |
| Rollback / undo | Undo is a **paid** feature | **built-in** `rollback` blocks (free) |
| Preconditions | not built-in | **preConditions** (dbms, columnExists, …) |
| Env-specific changes | separate locations / callbacks | **contexts** & **labels** |
| Adopt on existing DB | `baseline-on-migrate` | `changelogSync` / `MARK_RAN` preconditions |
| Feel | minimal, "just SQL, in order" | richer, database-agnostic, more features |

## Which to pick?

- **Flyway** if your team thinks in SQL, targets one database, and wants the
  simplest possible mental model: *ordered SQL files, applied once.*
- **Liquibase** if you need database-agnostic definitions, free rollbacks,
  preconditions, or context/label-driven environment differences.

Both are first-class in Spring Boot (`spring-boot-starter-flyway` /
`spring-boot-starter-liquibase`) and auto-run on startup. Use **one**, not both.

## The migration sequence (identical schema, two dialects)

1. create `author`
2. create `book` (+ FK to author + index)
3. add `author.email` (+ unique constraint)  ← evolving an existing table
4. seed sample data  ← plus the identity-restart gotcha (explicit ids don't bump the counter)
5. a `book_catalog` **view**  ← the re-runnable/redefinable object

See each project's `README.md` for the feature-by-feature walkthrough.
