# Kafka Streams from scratch — a runnable word-count walkthrough

A self-contained guide: scaffold a Spring Boot app, run a Kafka broker, create
topics, stream-count words, and watch it work. Copy-paste top to bottom.

---

## 0. Prerequisites (what the machine needs)

| Tool | Why | Check |
|---|---|---|
| Java 17+ | run the app | `java -version` |
| Maven (or the bundled `./mvnw`) | build | `mvn -version` |
| Spring CLI | `spring init` scaffolding | `spring --version` |
| A Docker daemon (Colima, Docker Desktop, …) | run the Kafka broker | `docker info` |

If you use **Colima** (lightweight Docker daemon for macOS):
