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
  of generating DDL. No migration scripts yet — none needed until the first entity lands.

### Changed
- Renamed group/artifact from `co.medina.portafolio:camedina-clients-service` to
  `co.medina.portfolio:clients-service` (fixing the `portafolio` typo and dropping the redundant
  `camedina-` prefix).
- Base package renamed from `co.medina.portafolio.camedinaclientsservice` to
  `co.medina.portfolio.clientsservice`.

## 0.0.1-SNAPSHOT

Initial Spring Initializr scaffold: `co.medina.portafolio:camedina-clients-service`, Spring Boot 4.1.0,
Java 26, Lombok + `spring-boot-configuration-processor`, empty `@SpringBootApplication` class, no
business logic.
