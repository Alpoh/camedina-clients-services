# Project plan

Working status/roadmap doc for `clients-service`. For the detailed change history see
`docs/CHANGELOG.md`; for design rationale see `docs/ARCHITECTURE.md`.

## Done so far

- **Project identity.** Renamed from the Spring Initializr defaults
  (`co.medina.portafolio:camedina-clients-service`) to `co.medina.portfolio:clients-service`, base
  package `co.medina.portfolio.clientsservice`. Version bumped to `0.0.2-SNAPSHOT`.
- **Tooling hygiene.** Comprehensive `.gitignore` for Java/Maven build output, IntelliJ (`.idea/`,
  `.run/`, `out/`), Eclipse/STS, NetBeans, VS Code, env/secret files, and OS cruft.
- **Persistence wired up.** `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` added.
  `compose.yaml` defines a real `postgres:17.2` service; `spring-boot-docker-compose` auto-starts and
  wires it for both `spring-boot:run` and `./mvnw test` (`spring.docker.compose.skip.in-tests=false`).
- **Docker packaging.** Multi-stage `Dockerfile` (Eclipse Temurin 26 JDK → JRE, Spring Boot 4 `tools`
  jarmode extraction, non-root `spring` user) plus `.dockerignore`. Verified it builds and runs
  end-to-end against the compose Postgres.
- **Documentation.** `docs/ARCHITECTURE.md`, `docs/CONTRIBUTING.md`, `docs/API.md`,
  `docs/CHANGELOG.md`, and this plan. `CLAUDE.md` kept in sync with all of the above as the single
  source of truth for AI-agent guidance.
- **Coverage tooling.** `jacoco-maven-plugin` bound to `./mvnw test`, reporting to
  `target/site/jacoco/`. JUnit 5/AssertJ/Mockito were already available via `spring-boot-starter-test` —
  no new test dependency needed for TDD, just the coverage visibility.
- **Web starter.** `spring-boot-starter-web` added (replacing the bare `spring-boot-starter`, which it
  pulls in transitively). The app now starts an embedded Tomcat and stays running instead of exiting
  right after context initialization — verified locally via `spring-boot:run` against the compose
  Postgres.
- **Bean Validation.** `spring-boot-starter-validation` added for `@Valid`/Bean Validation annotations
  on request DTOs (nothing to validate yet, no controllers exist).
- **Schema-migration strategy.** Flyway chosen over Liquibase: `spring-boot-starter-flyway` +
  `flyway-database-postgresql` added, `spring.jpa.hibernate.ddl-auto=validate` set so Hibernate only
  validates entities against the schema Flyway owns. Verified via `./mvnw test` — Flyway runs against
  the compose Postgres on startup, finds zero migrations (expected, no entities yet) and doesn't fail.
  No `db/migration` scripts exist yet; the first one lands with the first `@Entity`.

There is still **no business logic**: no entities, no repositories, no controllers.

## Suggested next steps

Roughly in the order they unblock each other; not a hard commitment, just a proposed path — revisit as
priorities change.

1. **Build the first feature vertical** (e.g. `client/`) as the template for package-by-feature:
   entity, repository, request/response `record` DTOs, service, controller, and its first Flyway
   migration under `src/main/resources/db/migration` (e.g. `V1__create_client_table.sql`).
2. **Global error handling.** `@ControllerAdvice` mapping validation/domain errors to `ProblemDetail`
   (RFC 7807), per the convention in `docs/ARCHITECTURE.md`.
3. **`spring-boot-starter-actuator`** for health/metrics — also gives the Dockerfile a real
   `HEALTHCHECK` target instead of none.
4. **API documentation** — `springdoc-openapi` for a live OpenAPI/Swagger UI, then fill in
   `docs/API.md`'s endpoint table as routes land. Now unblocked (web starter is in); still needs at
   least one controller to have anything to document.
5. **CI pipeline** (e.g. GitHub Actions) running `./mvnw verify` on push/PR — there's currently no
   automated gate beyond running tests locally.
6. **Testing depth.** Slice tests (`@WebMvcTest`, `@DataJpaTest`) per feature, plus the existing
   docker-compose-backed `@SpringBootTest` for full-context smoke coverage.
7. **Virtual threads.** Enable `spring.threads.virtual.enabled=true` once there's I/O-bound work
   (DB calls, external HTTP) worth benefiting from it.
8. **Security** (Spring Security / auth) once there's something worth protecting.

## How to update this doc

Check off / rewrite the "Done so far" section and trim "Suggested next steps" as work lands — treat it
as living, not a one-time snapshot. Move finished items into `docs/CHANGELOG.md`'s `[Unreleased]`
section as well.
