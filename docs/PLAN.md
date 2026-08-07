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
- **First feature vertical: `client/`.** `Client` (name, unique email) with independently-managed
  `Phone`/`Address` sub-resources (separate tables/endpoints, not nested in the client payload), each
  supporting multiple entries per client with a service-enforced "exactly one primary" invariant
  (backed by a DB partial unique index). Full CRUD, `Pageable`/`Page<T>` list endpoints per
  `docs/API.md`'s convention, Bean Validation at the boundary (including ISO-3166-1 alpha-2 country
  codes on addresses), `db/migration/V1__create_client_tables.sql`. Test coverage across
  `@DataJpaTest`/`@WebMvcTest`/plain Mockito unit tests, verified end-to-end via `spring-boot:run` +
  `curl`. `docs/API.md`'s endpoint table is now filled in.
- **Global error handling.** `NotFoundException`/`ConflictException` mapped to RFC 7807 `ProblemDetail`
  via a `@RestControllerAdvice` (`GlobalExceptionHandler`), plus `MethodArgumentNotValidException` → 400,
  landed together with the client vertical above.

- **Docker verification, Alpine base images, and `HEALTHCHECK`.** Rebuilt and ran the packaged image
  against the compose Postgres over its Docker network (manual `docker build`/`docker run` smoke test,
  not automated) — confirmed the full REST API works end-to-end from inside the container. Switched both
  Dockerfile stages from Debian-based `eclipse-temurin:26-jdk`/`26-jre` to `26-jdk-alpine`/`26-jre-alpine`
  (~40% smaller runtime image: ~312MB vs. ~517MB), which meant adjusting `addgroup`/`adduser` to
  BusyBox's short-flag syntax (`-S`/`-G`, not the Debian shadow-utils long flags) and writing the
  `HEALTHCHECK` around BusyBox `wget` instead of `bash`'s `/dev/tcp` (Alpine has neither `bash` nor
  `curl`). The healthcheck hits `/api/v1/clients` via `wget --spider`, so it now also verifies DB
  readiness, not just that Tomcat is listening. `compose.yaml`'s Postgres also switched to
  `postgres:17.2-alpine` (~36% smaller: ~398MB vs. ~620MB) — its `pg_isready` healthcheck needed no
  changes. Also fixed `jacoco-maven-plugin`'s version being unpinned (silently resolving to "latest
  release" at build time instead of anything managed by `spring-boot-starter-parent`, which the docs had
  wrongly assumed) by pinning it explicitly in `pom.xml`.
- **API documentation.** `springdoc-openapi-starter-webmvc-ui` (`2.8.6`) added — generates a live OpenAPI
  3.1 spec (`/v3/api-docs`) and Swagger UI (`/swagger-ui/index.html`) entirely from the existing
  controllers/DTOs/Bean Validation annotations, no hand-written spec. Verified working end-to-end
  (all 6 controllers/15 routes and every request/response schema showed up correctly) despite this
  version predating Spring Boot 4.1/Spring Framework 7's release by over a year — worth re-checking for a
  newer springdoc release later, but functionally solid today. One real gap found and fixed while
  actually testing it through the UI: `Pageable` controller parameters needed `@ParameterObject`
  (`org.springdoc.core.annotations`) to render as separate `page`/`size`/`sort` query params — without
  it, springdoc's automatic `Pageable` detection didn't activate on this stack, and Swagger UI's array
  widget for the un-flattened parameter sent malformed requests (`sort=["ASC"]`) that 500'd. Also renamed
  `getById`/`getAll` to `findById`/`findAll` across all three services/controllers — `get*` reads as a
  plain accessor even though these take arguments, hit the DB, and can throw.

There is still only one feature vertical (`client/`) — no other resources yet.

## Suggested next steps

Roughly in the order they unblock each other; not a hard commitment, just a proposed path — revisit as
priorities change.

1. **`spring-boot-starter-actuator`** for health/metrics — would let the Dockerfile's `HEALTHCHECK`
   target a proper `/actuator/health` endpoint instead of the business `/api/v1/clients` route it
   currently (pragmatically) reuses.
2. **CI pipeline** (e.g. GitHub Actions) running `./mvnw verify` on push/PR — there's currently no
   automated gate beyond running tests locally.
3. **Virtual threads.** Enable `spring.threads.virtual.enabled=true` once there's more I/O-bound work
   (DB calls, external HTTP) worth benefiting from it.
4. **Security** (Spring Security / auth) once there's something worth protecting — also when this
   lands, lock down `/swagger-ui/**`/`/v3/api-docs/**` outside dev/staging per `CLAUDE.md`'s convention.
5. **A second feature vertical** to validate the package-by-feature pattern generalizes beyond
   `client/` — also the trigger to hoist `NotFoundException`/`ConflictException`/`GlobalExceptionHandler`
   out of the `client` package into a shared one.

## How to update this doc

Check off / rewrite the "Done so far" section and trim "Suggested next steps" as work lands — treat it
as living, not a one-time snapshot. Move finished items into `docs/CHANGELOG.md`'s `[Unreleased]`
section as well.
