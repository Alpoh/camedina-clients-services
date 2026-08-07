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
  client/                       # first feature package
    Client.java                 (JPA entity)      Phone.java             Address.java
    ClientRepository.java                          PhoneRepository.java   AddressRepository.java
    ClientService.java                             PhoneService.java      AddressService.java
    ClientController.java                          PhoneController.java   AddressController.java
    ClientRequest.java / ClientResponse.java (record DTOs, one pair each per resource)
    AuditableEntity.java         # @MappedSuperclass: createdAt/updatedAt via @PrePersist/@PreUpdate
    NotFoundException.java / ConflictException.java / GlobalExceptionHandler.java (@RestControllerAdvice)
```
`Phone`/`Address` are independent sub-resources scoped by a plain `client_id` FK column — not a
bidirectional JPA relationship on `Client`. `NotFoundException`/`ConflictException`/
`GlobalExceptionHandler` live in `client/` for now since it's the only feature package; hoist them to a
shared package once a second vertical needs them too.

## Persistence

- `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime) are on the classpath.
- Three entities so far: `Client`, `Phone`, `Address` (all `client` package) — see
  [Package layout](#package-layout).
- **Schema migrations own the schema, Hibernate only validates.** `spring-boot-starter-flyway` +
  `flyway-database-postgresql` are on the runtime classpath, and `spring.jpa.hibernate.ddl-auto=validate`
  is set — Hibernate never generates DDL; it just checks entities against whatever Flyway has applied.
  Flyway runs automatically on startup (`spring-boot:run` and `./mvnw test`) against migrations on
  `classpath:db/migration` (default location), auto-configured for the same Postgres instance
  `spring-boot-docker-compose` wires up. `V1__create_client_tables.sql` creates `clients`,
  `client_phones`, `client_addresses` — including a partial unique index per phones/addresses table
  (`WHERE is_primary`) enforcing at most one primary row per client as defense-in-depth alongside the
  service-layer logic.
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
- `Dockerfile` — multi-stage build for the *application* image, both stages on **Alpine** base images
  (`eclipse-temurin:26-jdk-alpine` / `26-jre-alpine`) — the runtime image is ~40% smaller than the
  Debian-based `26-jre` tag (~312MB vs. ~517MB), verified with an actual `docker build` + `docker run`
  against the compose Postgres.
  1. `eclipse-temurin:26-jdk-alpine` builds the jar with the Maven wrapper and extracts it with Spring
     Boot 4's `tools` jarmode (`java -Djarmode=tools -jar target/*.jar extract --layers ...`), producing
     a thin application jar plus a `lib/` directory of dependency jars.
  2. `eclipse-temurin:26-jre-alpine` copies those layers in and runs as a non-root `spring` user via
     `java -jar app.jar`.
- The built image ships with no datasource configuration baked in; that's supplied by the deployment
  environment at runtime.
- **Alpine ships BusyBox, not GNU coreutils/bash** — this affects two things in the Dockerfile:
  `addgroup -S spring && adduser -S -G spring spring` (BusyBox's short-flag syntax, not Debian
  shadow-utils' long flags), and the `HEALTHCHECK`, which uses BusyBox `wget --spider -q -T 3` against
  `http://localhost:8080/api/v1/clients` (no `bash`/`curl` available to do a `/dev/tcp` trick or a curl
  check). Reusing a business endpoint for the healthcheck is a pragmatic stand-in — it does mean today's
  healthcheck actually verifies DB connectivity, not just "Tomcat is listening." Revisit with a dedicated
  `/actuator/health` check once `spring-boot-starter-actuator` lands (see `docs/PLAN.md`).

## Testing strategy

- `spring-boot-starter-webmvc-test` + `spring-boot-starter-data-jpa-test` (each transitively includes
  `spring-boot-starter-test`: JUnit 5, AssertJ, Mockito). Spring Boot 4.1 split `@WebMvcTest`/
  `@DataJpaTest`/`@AutoConfigureTestDatabase` out of `spring-boot-test-autoconfigure` into these
  dedicated starters, and replaced `@MockBean`/`@SpyBean` with `@MockitoBean`/`@MockitoSpyBean`.
- `@SpringBootTest` is reserved for cases that need the full context; slice tests (`@WebMvcTest`,
  `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = Replace.NONE)` so it hits the real compose
  Postgres instead of embedded H2) or plain Mockito unit tests are preferred otherwise.
- `spring.docker.compose.skip.in-tests=false` is set, so any test that boots the Spring context also
  starts the real Postgres container from `compose.yaml` — tests exercise the real database rather than
  H2 or mocks. This requires Docker to be running locally.
- `jacoco-maven-plugin` is bound to `./mvnw test` (agent attached via `prepare-agent`, HTML/XML/CSV
  report generated in the `test` phase itself). Reports land in `target/site/jacoco/`; no coverage
  threshold is enforced yet.

## Current status / roadmap

The `client` feature vertical is implemented end-to-end: `Client` plus independently-managed `Phone`/
`Address` sub-resources, full CRUD REST API, Flyway-owned schema, `ProblemDetail` error handling, and
test coverage across all three layers. See `docs/PLAN.md` for what's next (actuator, OpenAPI docs, CI,
a second feature vertical).
