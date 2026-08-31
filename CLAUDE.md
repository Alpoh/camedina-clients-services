# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Originally a freshly scaffolded Spring Boot project (generated via Spring Initializr); the first
business-logic feature vertical now exists (`client` package — see below). `compose.yaml` defines a
real Postgres service, and `spring-boot-docker-compose` starts/wires it automatically for both
`spring-boot:run` and `./mvnw test`.

- Group/artifact: `co.medina.portfolio:clients-service`
- Base package: `co.medina.portfolio.clientsservice`
- Spring Boot: 4.1.0 (via `spring-boot-starter-parent`)
- Java: 26 (`java.version` in `pom.xml`)
- Lombok + `spring-boot-configuration-processor` are wired into the annotation processor path
- Web: `spring-boot-starter-web` (embedded Tomcat) — the app starts and stays running
- Persistence: `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime). Five entities:
  `Client`, `Phone`, `Address`, `Project` (`client` package), `User` (`auth` package) — see below.
- Migrations: Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`, runtime) owns the
  schema; `spring.jpa.hibernate.ddl-auto=validate` means Hibernate never generates DDL, only validates
  entities against it. Flyway runs on every startup (`spring-boot:run` and `./mvnw test`) against
  `classpath:db/migration` (default location); `V1__create_client_tables.sql` creates `clients`,
  `client_phones`, `client_addresses`; `V2__create_projects_table.sql` creates `projects`;
  `V3__create_users_table.sql` creates `users`. The next migration should land together with whatever
  `@Entity` needs it, not before.
- `common` package (`co.medina.portfolio.clientsservice.common`): `AuditableEntity`
  (`@MappedSuperclass` — `createdAt`/`updatedAt` via `@PrePersist`/`@PreUpdate`), `NotFoundException`/
  `ConflictException`/`GlobalExceptionHandler` (`@RestControllerAdvice`). Hoisted out of `client` once
  `auth` (a second, genuinely independent vertical) needed them too — don't put feature-specific code
  here, only what's shared across verticals.
- First feature vertical: `client` package (`co.medina.portfolio.clientsservice.client`) — `Client`
  (name, unique email) plus independently-managed `Phone`/`Address`/`Project` sub-resources (own tables,
  own `/api/v1/clients/{clientId}/phones|addresses|projects` endpoints, not nested in the client
  payload; scoped by a plain `client_id` FK column, no bidirectional JPA relationship on `Client`). Each
  phone/address has a service-enforced "at most one primary per client" invariant (demoted on
  create/update, forced true when it's the client's only one), backed by a DB partial unique index
  (`WHERE is_primary`) as defense-in-depth — `Project` has no such concept. Full CRUD, `Pageable`/
  `Page<T>` list endpoints, Bean Validation (ISO-3166-1 alpha-2 country codes on addresses), errors
  mapped to RFC 7807 `ProblemDetail` via the shared `GlobalExceptionHandler` (see `common` package
  above). See `docs/API.md` for the full endpoint table. Query methods are named `findById`/`findAll`
  (not `getById`/`getAll`) — `get*` reads as a plain accessor, which these aren't (they take arguments,
  hit the DB, and can throw).
- Ops: `spring-boot-starter-actuator` is wired in, exposing `/actuator/health` (with DB liveness via the
  JPA/Datasource health indicator), `/actuator/info`, `/actuator/metrics`
  (`management.endpoints.web.exposure.include=health,info,metrics`). Component `show-details` stays at
  its default (`never`) — no client of this API is authorized to see component-level detail. The
  Dockerfile `HEALTHCHECK` targets `/actuator/health` instead of the business `/api/v1/clients` route it
  used to (pragmatic stand-in) reuse before actuator existed. `/actuator/health` is the one endpoint left
  open (`permitAll`, see below) so the container healthcheck — which sends no auth — keeps working.
- Second feature vertical: `auth` package (`co.medina.portfolio.clientsservice.auth`) — self-issued JWT
  authentication, no external identity provider. `User` (email, BCrypt `passwordHash`).
  `POST /api/v1/auth/register` and `POST /api/v1/auth/login` (both `permitAll`) return
  `{"token": "..."}`; every other endpoint requires `Authorization: Bearer <token>`.
  `JwtService` issues/validates HS256 tokens signed with `security.jwt.secret`
  (`security.jwt.expiration`, default `PT1H`) — a `@ConfigurationProperties` record (`JwtProperties`).
  `JwtAuthenticationFilter` (a plain `OncePerRequestFilter`, wired as a `@Bean` inside `SecurityConfig`,
  **not** `@Component`-scanned — a scanned `Filter` bean gets swept into every `@WebMvcTest` slice
  regardless of `addFilters`, and fails to construct there for lack of `JwtService`/
  `UserDetailsServiceImpl` in that minimal context; the same reasoning applies to
  `RestAuthenticationEntryPoint`) reads the bearer token per request. `SecurityConfig`:
  `SessionCreationPolicy.STATELESS`, CSRF disabled (no cookies, nothing to forge), `/api/v1/auth/**` and
  `/actuator/health` `permitAll`, everything else `authenticated()` — this also locks down Swagger UI
  and `/v3/api-docs`, and `/actuator/info`/`/actuator/metrics`, by default. A `security.swagger.permit-all`
  property (default `false`) additionally `permitAll`s `/swagger-ui/**`/`/v3/api-docs/**` when `true`;
  `application-local.properties` sets it `true`, activated via `SPRING_PROFILES_ACTIVE=local` — local dev
  convenience only, no `local` profile activation exists in `deploy.yml`/`ci-cd.yml`, so real deployments
  stay locked down. `RestAuthenticationEntryPoint` returns a `ProblemDetail` 401 (not Spring
  Security's default plain-text 401) for requests that reach a protected endpoint unauthenticated;
  `GlobalExceptionHandler`'s `AuthenticationException` handler covers the other case — bad credentials on
  `POST /api/v1/auth/login`, a manual `AuthenticationManager.authenticate()` call outside the filter
  chain. Both return a generic "Invalid credentials"/"Authentication required" detail — never which part
  was wrong, to avoid user enumeration. No roles/authorities concept yet, just authenticated-or-not;
  add one only when an endpoint actually needs to distinguish callers.
  `@WebMvcTest` classes for other controllers use `@AutoConfigureMockMvc(addFilters = false)` so they
  keep testing controller logic, not auth — `SecurityIntegrationTest` (`auth` package, `@SpringBootTest`)
  is the one test that exercises the real filter chain end to end.
- `Project` matches an external admin/portal frontend (not in this repo) that already mocks per-client
  projects with statuses. `ProjectStatus` is a fixed Java enum (`PLANNING`/`IN_PROGRESS`/`BLOCKED`/
  `REVIEW`/`DONE`) but serializes/deserializes as the frontend's existing lowercase-snake-case values
  (`planning`/`in_progress`/etc.) via Jackson `@JsonValue`/`@JsonCreator` — match an established external
  wire contract exactly rather than introducing a casing mismatch. Strictly single-client, no assignable
  staff — `User` now exists (`auth` package, see below) but nothing links a `Project` to one yet.
- **`Pageable` controller parameters need `@ParameterObject`** (`org.springdoc.core.annotations`) or
  springdoc renders `page`/`size`/`sort` as one opaque object query param instead of three separate,
  documented ones — confirmed broken without it on this springdoc/Spring Boot combo (springdoc's
  automatic `Pageable` detection didn't activate). Also: `sort`'s value format is `property,direction`
  (e.g. `name,asc`) — a bare direction like `sort=asc` is parsed as a property named `asc` and 500s.
- Test dependencies: `spring-boot-starter-webmvc-test` + `spring-boot-starter-data-jpa-test` (Spring
  Boot 4.1 split `@WebMvcTest`/`@DataJpaTest`/`@AutoConfigureTestDatabase` out of
  `spring-boot-test-autoconfigure` into these; `@MockBean`/`@SpyBean` were replaced by
  `@MockitoBean`/`@MockitoSpyBean`). Note Spring Boot 4.1 also defaults to Jackson 3.x, whose Maven
  coordinates and base package moved from `com.fasterxml.jackson.*` to `tools.jackson.*` — e.g.
  `ObjectMapper` is `tools.jackson.databind.ObjectMapper`, not the classic Jackson 2 package.
- API docs: `springdoc-openapi-starter-webmvc-ui:2.8.6` — Swagger UI at `/swagger-ui/index.html`, raw
  spec at `/v3/api-docs`, generated from the existing controllers/DTOs (no hand-written spec file).
  Verified working end-to-end even though this springdoc release predates Spring Boot 4.1/Spring
  Framework 7 by over a year (it pulls classic Jackson 2 alongside the app's Jackson 3, which is fine —
  springdoc uses its own internal `ObjectMapper` for spec generation, separate from Spring MVC's message
  converters). Re-check for a newer springdoc release periodically.

**Toolchain note:** this repo targets Java 26. The system default `java` may still be JDK 21
(`update-alternatives --list java`); a JDK 26 (Azul Zulu) is installed at `~/.jdks/azul-26.0.1` (added
via IntelliJ's JDK manager) but isn't necessarily on `PATH`/`JAVA_HOME` for shell use. If `./mvnw compile`
fails with `release version 26 not supported`, run it with
`JAVA_HOME=~/.jdks/azul-26.0.1 ./mvnw compile` or export `JAVA_HOME` for the shell.

## Documentation

Human-facing docs live in `docs/` — keep them (and this file) accurate to actual repo state as changes
land, not just aspirational:

- `docs/PLAN.md` — what's done and the proposed next steps to move the project forward. Check this
  first when picking up work; update it as items land. Its "Suggested next steps" summarizes and links
  into `docs/IMPLEMENTATION_PLAN.md` for the fuller architecture roadmap.
- `docs/IMPLEMENTATION_PLAN.md` — this repo's workstream of a cross-repo architecture review
  (`clients-infra/docs/ARCHITECTURE_IMPROVEMENTS.md`; sibling plans live in `clients-infra`'s and
  `clients-front`'s own `docs/IMPLEMENTATION_PLAN.md`), phased (0, 2, 3, 4) with gap IDs (`G1`…`G21`)
  tying back to that review. `docs/PLAN.md` remains the day-to-day status doc; this one is about *new
  architecture* not yet started — strike/move items into `docs/PLAN.md`/`docs/ARCHITECTURE.md`/
  `docs/CHANGELOG.md` as phases actually land, per its own "How to update this doc" section.
- `docs/CHANGELOG.md` — Keep a Changelog-style history, grouped under `[Unreleased]` until there's a
  first tagged release.
- `docs/ARCHITECTURE.md` — tech stack, package-by-feature convention, persistence/Docker/testing design.
- `docs/CONTRIBUTING.md` — prerequisites, build/test/run commands, code conventions, commit/PR expectations.
- `docs/API.md` — REST API conventions and the (currently empty) endpoint reference; fill in the table
  as controllers are added.

## Commands

Always use the Maven wrapper (`./mvnw`), not a system `mvn`, so the build uses the version pinned in `.mvn/wrapper`.

```bash
./mvnw compile                          # compile
./mvnw test                             # run all tests
./mvnw test -Dtest=ClassName            # run a single test class
./mvnw test -Dtest=ClassName#methodName # run a single test method
./mvnw verify                           # run tests + any bound verification (failsafe, etc. once added)
./mvnw spring-boot:run                  # run the app locally (starts compose.yaml services first, once any are defined)
./mvnw clean package                    # build the executable jar (target/*.jar)
./mvnw spring-boot:build-image          # build an OCI image without a Dockerfile (uses Cloud Native Buildpacks)
docker build -t clients-service .       # build the app image from the Dockerfile
docker compose up -d                    # start compose.yaml's Postgres for local dev, standalone (without spring-boot:run)
```

There is no linter/formatter plugin configured yet (no Checkstyle/Spotless in `pom.xml`). Don't assume one exists.

## Architecture

Standard single-module Maven/Spring Boot layout — no multi-module split, no hexagonal/layered package
structure established yet:

```
src/main/java/co/medina/portfolio/clientsservice/              # application code goes here
src/main/resources/application.properties                     # config
src/test/java/co/medina/portfolio/clientsservice/              # tests
compose.yaml                                                   # local dev dependencies (Postgres, Redis, etc.)
```

As real code is added, prefer package-by-feature (e.g. `client/`, `order/`) over package-by-layer
(`controller/`, `service/`, `repository/` at the top level) — it keeps each feature's Spring beans,
DTOs, and persistence code together and scales better as a solo/portfolio project grows.

## Spring Boot Docker Compose integration

`spring-boot-docker-compose` is on the runtime classpath (optional, only active when Docker Compose is
available). At `spring-boot:run` / app startup it will:

- read `compose.yaml` in the project root
- start any missing services automatically
- wire matching `spring.datasource.*` / connection properties for recognized images (Postgres, MySQL,
  Redis, MongoDB, etc.) without manual config

`compose.yaml` currently defines one service:
- `postgres` (`postgres:17.2-alpine`) — db/user/password all `clients-service`; only the container port
  (`5432`) is published, so Docker assigns a random host port and `spring-boot-docker-compose` reads it
  from the running container rather than a fixed port. Data persists in the named `postgres-data` volume
  across restarts (`docker compose down -v` to wipe it).

Implications when adding more services (Redis, etc.):
- Add real services (e.g. `redis:7`) to `compose.yaml` instead of hand-rolling `docker run` setups.
- Don't manually duplicate connection properties in `application.properties` for services this
  integration already auto-configures — that defeats the point of the dependency and can conflict.
- This integration is dev-only (`optional`, `runtime` scope) — it must not be relied on for how the
  app connects to services in a real deployment; production config still needs explicit
  `spring.datasource.*` (env vars / config server / secrets), since there's no compose file there.

**Tests use it too:** `spring.docker.compose.skip.in-tests=false` is set in `application.properties`
(Spring Boot's default is to skip Docker Compose during tests). This means `@SpringBootTest` — including
the plain `ClientsServiceApplicationTests` context-loads test — starts the real `postgres` container on
first use. Requires Docker to be running locally; expect the first test run to be slower while the
`postgres:17.2-alpine` image is pulled.

## Java 26 / Maven / Spring Boot conventions for this project

- **Records for DTOs/value objects.** Use `record` for request/response payloads and immutable value
  types instead of Lombok `@Data` classes — less generated code to reason about, and pairs naturally
  with Bean Validation (`record CreateClientRequest(@NotBlank String name, @Email String email) {}`).
- **Imports: explicit single-class imports only, no wildcard imports** (`import foo.bar.*;`) — applies
  equally to production and test code. Keeps `git diff`/review noise down (adding one new type only
  touches one import line) and makes it obvious at a glance what a file actually depends on.
- **`var` for local variables whenever the right-hand side already makes the type obvious** — constructor
  calls (`var client = new Client(...)`), static factory methods (`var id = UUID.randomUUID()`,
  `var page = PageRequest.of(0, 20)`), builder chains, etc. Applies in test code too (`var response =
  mockMvc.perform(...)`, `var request = new CreateClientRequest(...)`), not just production code. Keep an
  explicit type when the initializer doesn't make it obvious — a factory method returning an
  interface/generic-named type, or a numeric literal where the exact type (`long` vs `int`) matters and
  isn't visible from a bare literal. `var` isn't legal for fields, method parameters, or return types —
  those keep explicit types as usual.
- **Constructor injection only.** No field injection (`@Autowired` on fields). Lombok `@RequiredArgsConstructor`
  on `final` fields is fine for reducing boilerplate.
- **Prefer `application.yml`/`.properties` profiles** (`application-dev.properties`, `application-prod.properties`)
  over scattering `@Profile`-guarded beans, unless the branching is genuinely code, not config.
- **Lombok scope:** keep it to boilerplate reduction (`@Getter`, `@RequiredArgsConstructor`, `@Builder`
  on DTOs). Avoid `@Data` on JPA entities (its generated `equals`/`hashCode`/`toString` over all fields
  causes lazy-loading and recursion issues) — write entity `equals`/`hashCode` on the ID only, if needed.
- **Virtual threads:** Java 26 + Spring Boot 4 support virtual threads via
  `spring.threads.virtual.enabled=true`. Worth enabling for I/O-bound services (typical for a
  clients/CRUD service) rather than tuning platform-thread pool sizes.
- **Bean validation at the boundary.** Annotate controller method params with `@Valid` and let
  `MethodArgumentNotValidException` map to a `@ControllerAdvice`/`ProblemDetail` response rather than
  hand-checking nulls in service code.
- **`ProblemDetail` (RFC 7807) for error responses** — Spring's built-in support (`ErrorResponse`,
  `ProblemDetail`) over a custom error-body shape.
- **Testing:** `spring-boot-starter-test` is present (JUnit 5, AssertJ, Mockito — no separate
  dependencies needed for these). Use `@SpringBootTest` sparingly (slow, full context); prefer slice
  tests (`@WebMvcTest`, `@DataJpaTest`) or plain unit tests with Mockito for anything that doesn't need
  the full context. For tests needing real infra (DB, etc.), prefer Testcontainers over H2/mocks so
  tests match production behavior — pairs well with the `compose.yaml` services already used for local
  dev.
- **TDD: write the test first.** For new behavior (a service method, a controller endpoint, a validation
  rule) write a failing test that pins down the expected behavior before writing the implementation, then
  make it pass, then refactor. For bug fixes, write a test that reproduces the bug (red) before touching
  the fix (green) — it's the only way to be sure the fix actually addresses the reported behavior and
  that it stays fixed. Applies to both `@WebMvcTest`/`@DataJpaTest` slice tests and plain Mockito unit
  tests.
- **Coverage:** `jacoco-maven-plugin` is bound to `./mvnw test` (`prepare-agent` + a `report` execution
  in the `test` phase, version pinned explicitly in `pom.xml` — `spring-boot-starter-parent` does not
  manage it, so an unpinned version resolves to "latest release" at build time, which Maven flags as
  non-reproducible). Reports land in `target/site/jacoco/` (`index.html` for a browsable report,
  `jacoco.xml`/`jacoco.csv` for tooling) —
  no enforced coverage threshold yet, it's report-only.
- **Config properties:** for grouped settings, prefer a `@ConfigurationProperties`-annotated record
  (the configuration-processor annotation path is already wired in `pom.xml`) over multiple loose
  `@Value` injections.

## Web API / production best practices

General practices for a Spring Boot REST/JPA service, beyond the Java/Spring-specific conventions
above. Apply the "now" items directly; the "when X lands" items are conventions to follow once that
piece of the stack actually exists — don't build the infrastructure for them speculatively.

- **Never leak internals in error responses.** Unexpected exceptions must not reach the client as a raw
  stack trace/exception message — that's an information-disclosure risk (class names, SQL, file paths).
  `GlobalExceptionHandler` currently only maps `NotFoundException`/`ConflictException`/
  `MethodArgumentNotValidException`; anything else falls through to Spring Boot's default error handling,
  which can include a `trace` field depending on `server.error.include-stacktrace`. Add a catch-all
  `@ExceptionHandler(Exception.class)` mapping to a generic 500 `ProblemDetail` with a fixed, non-leaky
  detail message, and log the real exception server-side instead.
- **Never log PII.** `Client`/`Phone`/`Address` carry email, phone numbers, and street addresses — don't
  let entity `toString()` (already avoided by skipping `@Data`, see above) or ad-hoc `log.info(entity)`
  calls dump that into logs. Log identifiers (`client.getId()`), not the PII fields themselves.
- **Keep `@Transactional` methods short and free of I/O to other systems.** No outbound HTTP calls, no
  Thread.sleep, nothing slow inside a transaction — it holds a DB connection (and possibly locks) for the
  duration. Fine as-is today (our services only touch the DB), worth remembering as features grow.
- **Avoid N+1 queries once entity relationships exist.** None of `Client`/`Phone`/`Address` have JPA
  associations to each other today (by design — see Persistence above), so this doesn't bite yet; if a
  `@OneToMany`/`@ManyToOne` is ever added, fetch what's needed with a fetch-join query or `@EntityGraph`
  rather than looping and lazy-loading.
- **Secrets never get committed or baked into images.** Already the pattern for `spring.datasource.*` in
  prod (env vars, no compose file outside dev — see Docker Compose integration above); `security.jwt.secret`
  follows the same shape (`${SECURITY_JWT_SECRET:dev-only-...}` in `application.properties` — the
  fallback is a labeled dev-only placeholder, not a real secret, overridden via env var in any real
  deployment). Keep any future secret the same way.
- **CORS: likely never needed for this API.** The external admin/portal frontend (`clients-front`, a
  separate repo) integrates as a server-side proxy, not a direct browser client: it runs as its own ECS
  task, reaches `clients-service` over a private Cloud Map DNS name
  (`clients-service.dev.internal:8080`, provisioned in the separate `clients-infra` repo's
  `ecs-cluster.yaml`) gated by security groups, and only `clients-front` sits behind the public ALB. The
  browser only ever talks to `clients-front`'s origin, never to `clients-service` directly — so there's
  no cross-origin browser request for CORS to police here. If a *different*, genuinely browser-direct
  consumer ever appears, configure explicit allowed origins per environment (`CorsConfigurationSource`
  bean) then, rather than a wildcard `*`. See `docs/ARCHITECTURE.md`'s Deployment topology section.
- **Rate limiting / backpressure: once this API is reachable from the public internet**, add it at the
  edge (gateway/ingress) or via a library (e.g. Bucket4j) rather than hand-rolling per-endpoint counters.
- **TLS termination happens at the edge** (load balancer/ingress/reverse proxy), not in the Spring app
  itself — don't add an embedded-Tomcat SSL config for this. In this deployment, though, "the edge" is
  in front of `clients-front` only: `clients-infra`'s `cloudfront.yaml` puts CloudFront (default
  `*.cloudfront.net` cert) in front of the ALB, which fronts `clients-front`, to fix that app's `Secure`
  session cookies getting dropped by browsers over plain HTTP — `clients-service` is not behind that ALB
  or CloudFront at all (see Deployment topology in `docs/ARCHITECTURE.md`). `clients-front` calls
  `clients-service` server-side over the private Cloud Map DNS name, in plain HTTP, with no proxy hop in
  between — so there is no `X-Forwarded-Proto` for this app to trust, and no `server.forward-headers-strategy`
  setting is needed here (it would be inert: nothing on this path ever sets that header).
- **Health/readiness probes:** `spring-boot-starter-actuator` is wired in; the Dockerfile `HEALTHCHECK`
  targets `/actuator/health`, and any future orchestrator's liveness/readiness checks should do the same
  instead of a hand-rolled ping endpoint.
- **Authentication/authorization:** self-issued JWT auth (`auth` package, see above) — every endpoint
  except `/api/v1/auth/**` and `/actuator/health` requires a valid bearer token, which also means
  Swagger UI/`/v3/api-docs` and `/actuator/info`/`/actuator/metrics` are locked down today (no
  dev/staging profile split exists to scope that more precisely). Revisit whether `Project` needs an
  assignable-staff concept now that a real `User`/principal exists.
- **Structured/JSON logging in production**, plain console logging (current default) is fine for local
  dev — revisit when there's a real log aggregator to ship to.
- **Postgres column types must match what Hibernate expects under `ddl-auto=validate`**, or the app
  fails to start (hit this exact bug with `country CHAR(2)` vs. the `String` field's expected
  `VARCHAR(2)` — see `docs/CHANGELOG.md`). When adding a migration, match the JPA field type: `String` →
  `VARCHAR`/`TEXT` not `CHAR`, `Instant` → `TIMESTAMPTZ` not `TIMESTAMP`.
- **Migrations must stay backward-compatible with the currently-running app** during a rolling deploy —
  don't drop/rename a column or table in the same migration that removes the corresponding entity field;
  land the migration first (additive), deploy, then remove the now-unused column in a later migration.
- **Index every FK column** (already the pattern for `client_id` on `client_phones`/`client_addresses`)
  — Postgres does not do this automatically, unlike the primary key.
- **Prefer `@Slf4j` (Lombok) over a hand-declared `private static final Logger log = ...` field** once
  logging is added to a class — consistent with keeping Lombok scoped to boilerplate reduction.
- **OpenAPI/Swagger, once `springdoc-openapi` lands** (see `docs/PLAN.md`): let the spec generate from
  the existing controllers/DTOs (`@Valid`, Bean Validation annotations, and Javadoc already describe the
  shape) rather than growing a hand-maintained separate spec file that can drift from the real API. Use
  `@Schema`/`@Operation` descriptions only where the code doesn't already make intent obvious. Swagger UI
  now requires the same bearer-token auth as every other endpoint (see Authentication/authorization
  above) — there's no unauthenticated way to browse it, by design.

## Docker / packaging

- `Dockerfile` builds the app image via a multi-stage build, both stages on **Alpine** base images
  (`eclipse-temurin:26-jdk-alpine` / `26-jre-alpine`, not the Debian-based plain tags) — ~40% smaller
  runtime image (~312MB vs. ~517MB for `26-jre`). Alpine ships BusyBox, not GNU coreutils/bash — two
  concrete places that matters:
  - **`addgroup`/`adduser` use BusyBox's short-flag syntax**: `addgroup -S spring && adduser -S -G spring
    spring`, not Debian shadow-utils' `addgroup --system` / `adduser --system --ingroup`.
  - **No `bash`, no `curl` — only BusyBox `sh` and `wget`.** The `HEALTHCHECK` uses
    `wget --spider -q -T 3 http://localhost:8080/actuator/health`, not a `bash`-`/dev/tcp` trick. Actuator's
    health endpoint includes the JPA/Datasource health indicator, so the healthcheck still exercises DB
    connectivity, not just "Tomcat is listening" — it previously reused the business `/api/v1/clients`
    route for the same reason, before actuator existed.
  - `build` stage: runs `./mvnw package` (with a `/root/.m2` cache mount), then
    `java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted` — Spring Boot 4's
    `tools` jarmode (replaces the old 3.x `layertools` mode), which produces a thin `application/*.jar`
    (Main-Class + a `Class-Path` manifest entry pointing at `lib/*.jar`, no `JarLauncher` involved) plus
    `dependencies/lib/*.jar`.
  - `runtime` stage: copies `dependencies/lib/` → `./lib/` and the extracted jar → `./app.jar`, runs as a
    non-root `spring` user, `ENTRYPOINT ["java", "-jar", "app.jar"]`.
  - Dependency and application-code layers are copied separately so `docker build` cache reuse works when
    only application code changes.
  - `./mvnw spring-boot:build-image` (Cloud Native Buildpacks) remains a viable no-Dockerfile alternative
    if that's ever preferred over maintaining the Dockerfile.
- `.dockerignore` excludes `.git`, `.idea`, `.run`, `target`, and `*.md` from the build context.
- The built image has no baked-in `spring.datasource.*` — those must come from the deployment
  environment (e.g. `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` env vars), since
  `spring-boot-docker-compose` is dev-only and won't run inside the container.
- `compose.yaml` is for local development dependencies only (databases, caches, message brokers) — not
  for running the application itself. Don't add the app's own service to `compose.yaml`; that's what
  `spring-boot-docker-compose` + `spring-boot:run` is for.

## CI/CD

Two separate workflow files under `.github/workflows/` — not yet consolidated, see the open item in
`docs/PLAN.md`:

- **`ci-cd.yml`** (`build` + `docker` jobs) — the original build/publish gate. `build` runs
  `./mvnw verify` on every push/PR against `main`. Needs no special setup beyond
  `actions/setup-java@v4` (Temurin 26) — `ubuntu-latest` ships Docker, so `spring-boot-docker-compose`
  starting the real `postgres:17.2-alpine` from `compose.yaml` for the test suite works the same as it
  does locally; no Testcontainers/H2 substitution needed. `docker` (`needs: build`, gated with
  `if: github.event_name == 'push' && github.ref == 'refs/heads/main'` so it never runs on PRs or other
  branches) builds the existing `Dockerfile` and pushes to GHCR (`ghcr.io/alpoh/camedina-clients-services`,
  tags `latest` and `${{ github.sha }}`), authenticated via the workflow's own `secrets.GITHUB_TOKEN`
  with a job-scoped `packages: write` permission — no registry credential to provision or rotate. A
  pushed GHCR package still defaults to **private** even though the repo is public; that visibility
  toggle lives in the package's own GHCR settings and can't be set from the workflow.
- **`deploy.yml`** (`test` + `build-and-deploy` jobs) — picks the deploy target `ci-cd.yml` deliberately
  left open: AWS ECS. Triggers on push to `main`. `test` re-runs `./mvnw test`; `build-and-deploy`
  (`needs: test`) authenticates to AWS via OIDC (`permissions: id-token: write`,
  `aws-actions/configure-aws-credentials@v4` assuming
  `arn:aws:iam::997979358457:role/camedina-dev-github-app-role` in `eu-west-1` — no long-lived AWS
  credential stored in Actions secrets), logs into ECR (`aws-actions/amazon-ecr-login@v2`), builds the
  same `Dockerfile` and pushes it to ECR (`camedina-dev-clients-service`, tags `latest` and
  `${{ github.sha }}`), then forces a new ECS deployment (`aws ecs update-service --force-new-deployment`
  on cluster `camedina-dev-cluster` / service `camedina-dev-clients-service`). The IAM role/OIDC trust
  relationship and the ECR repo/ECS cluster/service themselves are **not** created by this workflow —
  they must already exist in the AWS account for it to succeed; nothing in this repo provisions them
  (no Terraform/CDK yet).
- **Known duplication, not yet cleaned up:** both workflows build the same `Dockerfile` on every push to
  `main` — `ci-cd.yml` pushes it to GHCR, `deploy.yml` independently rebuilds and pushes it to ECR. GHCR
  is effectively unused as a deploy source now that ECS pulls from ECR; worth collapsing to a single
  build shared by both destinations (or dropping the GHCR push) rather than running two full Docker
  builds per push indefinitely.
