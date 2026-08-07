# Architecture

## Overview

`clients-service` is a single-module Maven/Spring Boot application. There is no multi-module split and
no hexagonal/layered top-level structure — see [Package layout](#package-layout) below.

| Concern         | Choice                                                             |
|-----------------|---------------------------------------------------------------------|
| Language        | Java 26                                                              |
| Framework       | Spring Boot 4.1.0 (via `spring-boot-starter-parent`)                 |
| Build           | Maven (wrapper-pinned, `./mvnw`)                                     |
| Web             | Spring MVC (`spring-boot-starter-web`), embedded Tomcat              |
| Persistence     | Spring Data JPA + Hibernate, PostgreSQL                              |
| Migrations      | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) |
| Local infra     | Docker Compose (`compose.yaml`), auto-wired by `spring-boot-docker-compose` |
| Packaging       | Multi-stage `Dockerfile` (Eclipse Temurin 26), or Cloud Native Buildpacks via `spring-boot:build-image` |
| Boilerplate     | Lombok (constructor/getter generation only — not `@Data` on entities) |

## Package layout

Base package: `co.medina.portfolio.clientsservice`.

As real features are added, prefer **package-by-feature** (`client/`, `order/`, ...) over
**package-by-layer** (`controller/`, `service/`, `repository/` at the top level). Each feature package
should own its Spring beans (controller, service), DTOs (as `record`s), and persistence code
(entity + repository) together. This keeps related code co-located and scales better than a layered
split as the service grows.

```
src/main/java/co/medina/portfolio/clientsservice/
  ClientsServiceApplication.java
  client/                 # example future feature package
    ClientController.java
    ClientService.java
    Client.java            (JPA entity)
    ClientRepository.java
    CreateClientRequest.java (record DTO)
```

## Persistence

- `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime) are on the classpath.
- No entities or repositories exist yet — the JPA/Hibernate auto-configuration is wired but idle until
  the first `@Entity` is added.
- **Schema migrations own the schema, Hibernate only validates.** `spring-boot-starter-flyway` +
  `flyway-database-postgresql` are on the runtime classpath, and `spring.jpa.hibernate.ddl-auto=validate`
  is set — Hibernate never generates DDL; it just checks entities against whatever Flyway has applied.
  Flyway runs automatically on startup (`spring-boot:run` and `./mvnw test`) against migrations on
  `classpath:db/migration` (default location), auto-configured for the same Postgres instance
  `spring-boot-docker-compose` wires up. No migration scripts exist yet since there are no entities —
  the first `V1__*.sql` lands together with the first `@Entity`.
- Local dev and tests get a real Postgres instance for free: `compose.yaml` defines a `postgres:17.2`
  service, and `spring-boot-docker-compose` starts it and injects `spring.datasource.*` automatically for
  both `./mvnw spring-boot:run` and `./mvnw test` (see [Local development & Docker](#local-development--docker)).
- Production `spring.datasource.*` must come from real deployment config (env vars, secrets, a config
  server) — there's no compose file outside local dev.

## Configuration & error handling conventions

These are the intended conventions for this service as endpoints are built out (see `CLAUDE.md` for the
full list aimed at AI coding agents):

- DTOs/value objects as `record`s, validated at the boundary with `@Valid` + Bean Validation annotations.
- `MethodArgumentNotValidException` and friends map to `ProblemDetail` (RFC 7807) responses via a
  `@ControllerAdvice`, rather than a hand-rolled error body shape.
- Constructor injection only — no field `@Autowired`.
- Config grouped as `@ConfigurationProperties` records rather than scattered `@Value` fields.
- Profile-specific behavior lives in `application-<profile>.properties`, not `@Profile`-guarded beans,
  unless the branching is genuinely code rather than config.

## Local development & Docker

- `compose.yaml` — **local dev dependencies only** (currently just Postgres). It intentionally does not
  run the application itself.
- `Dockerfile` — multi-stage build for the *application* image:
  1. `eclipse-temurin:26-jdk` builds the jar with the Maven wrapper and extracts it with Spring Boot 4's
     `tools` jarmode (`java -Djarmode=tools -jar target/*.jar extract --layers ...`), producing a thin
     application jar plus a `lib/` directory of dependency jars.
  2. `eclipse-temurin:26-jre` copies those layers in and runs as a non-root `spring` user via
     `java -jar app.jar`.
- The built image ships with no datasource configuration baked in; that's supplied by the deployment
  environment at runtime.

## Testing strategy

- `spring-boot-starter-test` (JUnit 5, AssertJ, Mockito).
- `@SpringBootTest` is reserved for cases that need the full context; slice tests (`@WebMvcTest`,
  `@DataJpaTest`) or plain Mockito unit tests are preferred otherwise.
- `spring.docker.compose.skip.in-tests=false` is set, so any test that boots the Spring context also
  starts the real Postgres container from `compose.yaml` — tests exercise the real database rather than
  H2 or mocks. This requires Docker to be running locally.
- `jacoco-maven-plugin` is bound to `./mvnw test` (agent attached via `prepare-agent`, HTML/XML/CSV
  report generated in the `test` phase itself). Reports land in `target/site/jacoco/`; no coverage
  threshold is enforced yet.

## Current status / roadmap

`spring-boot-starter-web` is on the classpath: the application starts an embedded Tomcat and stays
running. No business logic exists yet — no controllers, entities, or repositories. Adding the first
feature package (e.g. `client/`) is the natural next step.
