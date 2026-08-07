# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/) once it has its first release — until then, everything below
lives under `[Unreleased]`.

## [Unreleased] — 0.0.3-SNAPSHOT

### Added
- `spring-boot-starter-web`, replacing the bare `spring-boot-starter` (pulled in transitively). The app
  now starts an embedded Tomcat and stays running instead of exiting right after context
  initialization.
- Postgres persistence: `spring-boot-starter-data-jpa` + `org.postgresql:postgresql`, backed locally by
  a real `postgres:17.2` service in `compose.yaml`, auto-wired via `spring-boot-docker-compose` for both
  `spring-boot:run` and `./mvnw test`.
- `Dockerfile`: multi-stage build (Eclipse Temurin 26 JDK → JRE), non-root runtime user, Spring Boot 4
  `tools` jarmode extraction for layer-friendly image builds.
- `.dockerignore` for lean Docker build contexts.
- Comprehensive `.gitignore` covering Java/Maven build artifacts, IntelliJ (`.idea/`, `.run/`, `out/`),
  Eclipse/STS, NetBeans, VS Code, env/secrets files, and OS cruft.
- `docs/` folder (this changelog, `ARCHITECTURE.md`, `CONTRIBUTING.md`, `API.md`, `PLAN.md`).
- `jacoco-maven-plugin`, bound to `./mvnw test`, reporting coverage to `target/site/jacoco/`.
- `spring-boot-starter-validation` for `@Valid`/Bean Validation annotations on request DTOs.
- Flyway schema migrations: `spring-boot-starter-flyway` + `flyway-database-postgresql`, with
  `spring.jpa.hibernate.ddl-auto=validate` so Hibernate validates against the schema Flyway owns instead
  of generating DDL.
- First feature vertical: `client` package with `Client` (name, unique email) and independently-managed
  `Phone`/`Address` sub-resources (own tables/endpoints, multiple entries per client, a service-enforced
  "exactly one primary" invariant backed by a DB partial unique index). Full CRUD REST API under
  `/api/v1/clients...` with `Pageable`/`Page<T>` list endpoints and Bean Validation (including
  ISO-3166-1 alpha-2 country codes on addresses). `db/migration/V1__create_client_tables.sql`.
- Global error handling: `NotFoundException`/`ConflictException` mapped to RFC 7807 `ProblemDetail` via
  `GlobalExceptionHandler` (`@RestControllerAdvice`), plus `MethodArgumentNotValidException` → 400.
- Test coverage for the client vertical: `@DataJpaTest` repository tests, `@WebMvcTest` controller
  tests, and plain Mockito service unit tests.

### Changed
- Renamed group/artifact from `co.medina.portafolio:camedina-clients-service` to
  `co.medina.portfolio:clients-service` (fixing the `portafolio` typo and dropping the redundant
  `camedina-` prefix).
- Base package renamed from `co.medina.portafolio.camedinaclientsservice` to
  `co.medina.portfolio.clientsservice`.
- Test dependencies: `spring-boot-starter-test` replaced with `spring-boot-starter-webmvc-test` +
  `spring-boot-starter-data-jpa-test` (each transitively includes it). Spring Boot 4.1 split
  `@WebMvcTest`/`@DataJpaTest`/`@AutoConfigureTestDatabase` out of `spring-boot-test-autoconfigure` into
  these dedicated starters, and replaced `@MockBean`/`@SpyBean` with `@MockitoBean`/`@MockitoSpyBean`.

## 0.0.1-SNAPSHOT

Initial Spring Initializr scaffold: `co.medina.portafolio:camedina-clients-service`, Spring Boot 4.1.0,
Java 26, Lombok + `spring-boot-configuration-processor`, empty `@SpringBootApplication` class, no
business logic.
