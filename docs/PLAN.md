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
  `compose.yaml` defines a real `postgres:17.2-alpine` service; `spring-boot-docker-compose` auto-starts and
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
- **`Project` — second feature vertical.** A separate admin/portal frontend (not in this repo) is
  already built around per-client projects with statuses; the backend previously had no `Project`
  resource. Added as a sub-resource of `Client` at `/api/v1/clients/{clientId}/projects` (own table,
  `client_id` FK, `ON DELETE CASCADE`, mirrors the `Phone`/`Address` CRUD pattern exactly — no "primary"
  concept needed here), `db/migration/V2__create_projects_table.sql`. Strictly single-client, no
  assignable staff (no `User`/auth concept exists in this backend yet). Status is a fixed enum
  (`PLANNING`/`IN_PROGRESS`/`BLOCKED`/`REVIEW`/`DONE` in Java) matched to the frontend's existing
  lowercase-snake-case mocks (`planning`/`in_progress`/`blocked`/`review`/`done`) via Jackson
  `@JsonValue`/`@JsonCreator` — confirmed round-tripping correctly on the wire, including in the
  generated OpenAPI schema's enum values, not just at runtime. Test coverage across
  `@DataJpaTest`/`@WebMvcTest`/plain Mockito unit tests (47 total tests now), verified end-to-end via
  the running app.
- **Global exception handling closed a real gap.** While testing `Project`'s status validation, an
  invalid enum value on the request body (`HttpMessageNotReadableException`, thrown during JSON
  deserialization before Bean Validation even runs) fell through `GlobalExceptionHandler` entirely and
  leaked a full raw stack trace via Spring Boot's default error handling — exactly the risk flagged in
  `CLAUDE.md`'s "never leak internals in error responses" rule, just not yet implemented. Added handlers
  for `HttpMessageNotReadableException` (400, fixed non-leaky detail) and a catch-all `Exception` handler
  (500, fixed detail, logs the real exception server-side via `@Slf4j`) — closes that gap for every
  endpoint, not just `Project`'s.

`Project` lives in the same `client` package as `Client`/`Phone`/`Address` (same reasoning as the
existing sub-resources) — still one feature vertical, not two.

- **`spring-boot-starter-actuator`** added for health/metrics: `/actuator/health` (with DB liveness via
  the JPA/Datasource health indicator), `/actuator/info`, `/actuator/metrics` exposed
  (`management.endpoints.web.exposure.include=health,info,metrics`; component `show-details` stays at
  its `never` default — no auth yet to gate it behind). The Dockerfile `HEALTHCHECK` now targets
  `/actuator/health` instead of the business `/api/v1/clients` route it pragmatically reused before.
- **Public portfolio repo.** Added `README.md` (project overview, tech stack, getting-started commands)
  and an MIT `LICENSE` (declared in `pom.xml`'s `<licenses>` too). The GitHub repo
  (`Alpoh/camedina-clients-services`) is now public.
- **Spring Security — second, genuinely independent feature vertical: `auth/`.** Self-issued JWT auth
  (chosen over HTTP Basic/a shared API key — this is the pattern a real backend behind the admin/portal
  frontend would use, and it exercises more of Spring Security: `UserDetailsService`, `PasswordEncoder`,
  a custom filter, token issuance/validation). `User` (email, BCrypt-hashed password),
  `db/migration/V3__create_users_table.sql`. `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
  (`permitAll`, return `{"token": "..."}`); every other endpoint now requires
  `Authorization: Bearer <token>` — including Swagger UI/`/v3/api-docs` and `/actuator/info`/
  `/actuator/metrics` (no dev/staging profile split exists yet to scope that more precisely; only
  `/actuator/health` stays open, for the Dockerfile `HEALTHCHECK`). `JwtService` (HS256, `security.jwt.secret`
  from `SECURITY_JWT_SECRET` env var / a labeled dev-only default) + `JwtAuthenticationFilter`, both
  wired as `@Bean`s inside `SecurityConfig` rather than `@Component`-scanned — a scanned `Filter` bean
  gets swept into every `@WebMvcTest` slice regardless of `addFilters` and fails to construct there
  (found by actually running the test suite after adding Spring Security to the classpath, not by
  inspection). `RestAuthenticationEntryPoint` + `GlobalExceptionHandler`'s new `AuthenticationException`
  handler both return RFC 7807 `ProblemDetail` 401s with a generic "invalid credentials" detail — no
  user enumeration. No roles/authorities yet, just authenticated-or-not.
  Being the second independent vertical (not a `Client` sub-resource) also triggered the planned hoist:
  `AuditableEntity`/`NotFoundException`/`ConflictException`/`GlobalExceptionHandler` moved from `client`
  into a new shared `common` package, since `auth` needed them too. Verified end-to-end both via the
  automated suite (`SecurityIntegrationTest` exercises the real filter chain; existing `@WebMvcTest`
  classes got `@AutoConfigureMockMvc(addFilters = false)` to keep testing controller logic, not auth) and
  manually via `spring-boot:run` + `curl` (register → 201 + token; duplicate email → 409; login with bad
  password → 401; `/api/v1/clients` without a token → 401, with one → 200; `/actuator/health` open
  without a token; Swagger's `/v3/api-docs` now 401 without one).
- **CI/CD via GitHub Actions.** `.github/workflows/ci-cd.yml`: a `build` job runs `./mvnw verify` on every
  push/PR against `main` (the previously-missing automated gate — Docker's available on the `ubuntu-latest`
  runner, so `spring-boot-docker-compose` starting Postgres for the test suite works the same as locally).
  A `docker` job (`needs: build`, gated to `push` events on `main` only — not PRs, not other branches)
  builds the existing multi-stage `Dockerfile` and pushes it to GHCR as
  `ghcr.io/alpoh/camedina-clients-services:latest` and `:<git-sha>`, authenticated with the workflow's own
  `GITHUB_TOKEN` (`packages: write` permission) — no registry secret to manage. Deploying that image
  somewhere it actually runs is still open, see below. One manual one-time step this can't automate: a
  package pushed via `GITHUB_TOKEN` defaults to **private** regardless of the repo's public visibility —
  toggle it public in the package's own GHCR settings if it should be publicly pullable.

## Suggested next steps

Roughly in the order they unblock each other; not a hard commitment, just a proposed path — revisit as
priorities change.

1. **Actually deploy the GHCR image somewhere.** CI/CD currently stops at "image is published to GHCR" —
   nothing pulls and runs it. Needs a target platform decided (e.g. Fly.io/Render/a VPS over SSH) and,
   once chosen, a follow-up job/workflow added to `ci-cd.yml` with whatever credentials that target needs
   as GitHub Actions secrets.
2. **Virtual threads.** Enable `spring.threads.virtual.enabled=true` once there's more I/O-bound work
   (DB calls, external HTTP) worth benefiting from it.
3. **Roles/authorities**, once an endpoint actually needs to distinguish callers (e.g. an assignable-staff
   concept for `Project`, now that a real `User`/principal exists) — today's auth is deliberately just
   authenticated-or-not.
4. **Refresh tokens / logout**, if session length in practice turns out to need it — today's JWTs are
   short-lived (`security.jwt.expiration`, default `PT1H`) with no revocation mechanism, which is fine for
   a portfolio service but worth flagging as a real gap for anything beyond that.

## How to update this doc

Check off / rewrite the "Done so far" section and trim "Suggested next steps" as work lands — treat it
as living, not a one-time snapshot. Move finished items into `docs/CHANGELOG.md`'s `[Unreleased]`
section as well.
