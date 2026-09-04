# Liquibase + Spring Boot — end-to-end demo

Same "library" app as the Flyway demo, but the schema is owned by **Liquibase YAML changelogs**. Hibernate runs `ddl-auto=validate` only — it never writes DDL.

Spring Boot 4.1.0 · Java 17 · H2 (zero setup, PostgreSQL-ready) · Liquibase.

## Run it

```bash
./mvnw spring-boot:run
```

- App: http://localhost:8080
- H2 console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:library`, user `sa`, no password)

Liquibase runs automatically on startup against `spring.liquibase.change-log`.

## API

| Method | Path           | Purpose                                   |
|--------|----------------|--------------------------------------------|
| GET    | `/api/authors` | List authors (id, name, email)             |
| GET    | `/api/books`   | List books joined to author name           |
| POST   | `/api/authors` | Create an author — proves writes work against the migrated schema |

## Schema (changelogs)

Master: `db.changelog-master.yaml` — asserts DB engine (h2/postgresql) then includes changesets in order.

| File | ChangeSet(s) | Does |
|------|------|------|
| `v1__create-author.yaml` | v1-create-author | `author(id, name)` |
| `v2__create-book.yaml` | v2-create-book-table, v2-fk-book-author, v2-idx-book-author | `book(id, title, published_year, author_id)` + FK + index — one logical step split into small, independently-tracked changesets |
| `v3__add-author-email.yaml` | v3-add-author-email, v3-unique-author-email | Adds `author.email` (guarded by `columnExists` precondition, `onFail: MARK_RAN`) + unique constraint |
| `v4__seed-data.yaml` | v4-seed-authors, v4-seed-books | Sample rows, gated by `context: dev` (see `spring.liquibase.contexts`) |
| `v5__book-catalog-view.yaml` | v5-book-catalog-view | `book_catalog` view (author+book join), `runOnChange: true` so edits re-apply on restart |

Each changeset = `(id, author)`, applied at most once, tracked in `DATABASECHANGELOG`. Most carry explicit `rollback:` blocks.

## Switching to PostgreSQL

Changelogs are dialect-agnostic. Just swap the datasource block in `application.properties` (commented example included) — no changelog edits needed.

## Liquibase vs Flyway (this demo's point)

- Changelogs are structured YAML, not raw SQL — Liquibase generates dialect-correct DDL.
- Built-in preconditions (`columnExists`, `dbms`, ...) and `onFail` policies for safe/idempotent adoption.
- `context`-gated changesets (dev seed data vs prod) without separate migration files.
- `runOnChange` + `replaceIfExists` for repeatable objects (views, procs) — Flyway's "repeatable migrations" equivalent.
