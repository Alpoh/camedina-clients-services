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
- Persistence: `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime). Four entities:
  `Client`, `Phone`, `Address`, `Project` (`client` package) — see below.
- Migrations: Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`, runtime) owns the
  schema; `spring.jpa.hibernate.ddl-auto=validate` means Hibernate never generates DDL, only validates
  entities against it. Flyway runs on every startup (`spring-boot:run` and `./mvnw test`) against
  `classpath:db/migration` (default location); `V1__create_client_tables.sql` creates `clients`,
  `client_phones`, `client_addresses`; `V2__create_projects_table.sql` creates `projects`. The next
  migration should land together with whatever `@Entity` needs it, not before.
- First feature vertical: `client` package (`co.medina.portfolio.clientsservice.client`) — `Client`
  (name, unique email) plus independently-managed `Phone`/`Address`/`Project` sub-resources (own tables,
  own `/api/v1/clients/{clientId}/phones|addresses|projects` endpoints, not nested in the client
  payload; scoped by a plain `client_id` FK column, no bidirectional JPA relationship on `Client`). Each
  phone/address has a service-enforced "at most one primary per client" invariant (demoted on
  create/update, forced true when it's the client's only one), backed by a DB partial unique index
  (`WHERE is_primary`) as defense-in-depth — `Project` has no such concept. Full CRUD, `Pageable`/
  `Page<T>` list endpoints, Bean Validation (ISO-3166-1 alpha-2 country codes on addresses),
  `NotFoundException`/`ConflictException` mapped to RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`
  (`@RestControllerAdvice`), which also has a `HttpMessageNotReadableException` handler (malformed JSON
  body, e.g. a bad enum value → clean 400) and a catch-all `Exception` handler (500, fixed detail, logs
  server-side) so nothing leaks a raw stack trace. See `docs/API.md` for the full endpoint table. Query
  methods are named `findById`/`findAll` (not `getById`/`getAll`) — `get*` reads as a plain accessor,
  which these aren't (they take arguments, hit the DB, and can throw).
- Ops: `spring-boot-starter-actuator` is wired in, exposing `/actuator/health` (with DB liveness via the
  JPA/Datasource health indicator), `/actuator/info`, `/actuator/metrics`
  (`management.endpoints.web.exposure.include=health,info,metrics`). Component `show-details` stays at
  its default (`never`) since there's no auth yet — don't flip it to `always`/`when-authorized` until
  Spring Security lands (see `docs/PLAN.md`'s next step). The Dockerfile `HEALTHCHECK` targets
  `/actuator/health` instead of the business `/api/v1/clients` route it used to (pragmatic stand-in)
  reuse before actuator existed.
- No auth/`User` concept exists yet — **Spring Security is the next planned addition** (see
  `docs/PLAN.md`). Until it lands, every endpoint (including Swagger UI, `/v3/api-docs`, and the
  actuator endpoints above) is unauthenticated; don't assume any request is trusted/authorized.
- `Project` matches an external admin/portal frontend (not in this repo) that already mocks per-client
  projects with statuses. `ProjectStatus` is a fixed Java enum (`PLANNING`/`IN_PROGRESS`/`BLOCKED`/
  `REVIEW`/`DONE`) but serializes/deserializes as the frontend's existing lowercase-snake-case values
  (`planning`/`in_progress`/etc.) via Jackson `@JsonValue`/`@JsonCreator` — match an established external
  wire contract exactly rather than introducing a casing mismatch. Strictly single-client, no assignable
  staff (no `User`/auth concept exists in this backend yet).
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
  first when picking up work; update it as items land.
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
  prod (env vars, no compose file outside dev — see Docker Compose integration above); keep any future
  secret (API keys, JWT signing keys) the same way.
- **CORS: when a frontend/browser consumer exists**, configure explicit allowed origins per environment
  (`CorsConfigurationSource` bean) rather than a wildcard `*` — there's no browser-facing consumer yet,
  so nothing to configure today.
- **Rate limiting / backpressure: once this API is reachable from the public internet**, add it at the
  edge (gateway/ingress) or via a library (e.g. Bucket4j) rather than hand-rolling per-endpoint counters.
- **TLS termination happens at the edge** (load balancer/ingress/reverse proxy), not in the Spring app
  itself — don't add an embedded-Tomcat SSL config for this.
- **Health/readiness probes:** `spring-boot-starter-actuator` is wired in; the Dockerfile `HEALTHCHECK`
  targets `/actuator/health`, and any future orchestrator's liveness/readiness checks should do the same
  instead of a hand-rolled ping endpoint.
- **Authentication/authorization: Spring Security is the next planned addition** (see `docs/PLAN.md`) —
  there's nothing protecting any endpoint today. When it lands: lock down `/swagger-ui/**`/
  `/v3/api-docs/**` outside dev/staging, restrict actuator endpoints beyond `/actuator/health` to
  authenticated/authorized requests (and reconsider `show-details` above), and revisit whether `Project`
  needs an assignable-staff concept once a real `User`/principal exists.
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
  `@Schema`/`@Operation` descriptions only where the code doesn't already make intent obvious. Don't
  expose Swagger UI outside dev/staging without auth once the app is public.

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
