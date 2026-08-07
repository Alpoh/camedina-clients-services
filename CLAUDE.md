# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is a freshly scaffolded Spring Boot project (generated via Spring Initializr) with no business
logic yet — a single `@SpringBootApplication` class, no controllers/entities/repositories.
`compose.yaml` defines a real Postgres service, and `spring-boot-docker-compose` starts/wires it
automatically for both `spring-boot:run` and `./mvnw test`.

- Group/artifact: `co.medina.portfolio:clients-service`
- Base package: `co.medina.portfolio.clientsservice`
- Spring Boot: 4.1.0 (via `spring-boot-starter-parent`)
- Java: 26 (`java.version` in `pom.xml`)
- Lombok + `spring-boot-configuration-processor` are wired into the annotation processor path
- Web: `spring-boot-starter-web` (embedded Tomcat) — the app starts and stays running
- Persistence: `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime) — no entities/
  repositories yet, but the `DataSource`/Hibernate stack is live and requires a reachable Postgres to
  start the context (local dev gets this for free from `compose.yaml`)
- Migrations: Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`, runtime) owns the
  schema; `spring.jpa.hibernate.ddl-auto=validate` means Hibernate never generates DDL, only validates
  entities against it. Flyway runs on every startup (`spring-boot:run` and `./mvnw test`) against
  `classpath:db/migration` (default location) — currently empty since there are no entities yet. The
  first migration (`V1__*.sql`) should land together with the first `@Entity`, not before.

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
- `postgres` (`postgres:17.2`) — db/user/password all `clients-service`; only the container port
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
`postgres:17.2` image is pulled.

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
  in the `test` phase, version managed by `spring-boot-starter-parent`). Reports land in
  `target/site/jacoco/` (`index.html` for a browsable report, `jacoco.xml`/`jacoco.csv` for tooling) —
  no enforced coverage threshold yet, it's report-only.
- **Config properties:** for grouped settings, prefer a `@ConfigurationProperties`-annotated record
  (the configuration-processor annotation path is already wired in `pom.xml`) over multiple loose
  `@Value` injections.

## Docker / packaging

- `Dockerfile` builds the app image via a multi-stage build:
  - `build` stage: `eclipse-temurin:26-jdk`, runs `./mvnw package` (with a `/root/.m2` cache mount),
    then `java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted` — Spring Boot
    4's `tools` jarmode (replaces the old 3.x `layertools` mode), which produces a thin `application/*.jar`
    (Main-Class + a `Class-Path` manifest entry pointing at `lib/*.jar`, no `JarLauncher` involved) plus
    `dependencies/lib/*.jar`.
  - `runtime` stage: `eclipse-temurin:26-jre`, copies `dependencies/lib/` → `./lib/` and the extracted
    jar → `./app.jar`, runs as a non-root `spring` user, `ENTRYPOINT ["java", "-jar", "app.jar"]`.
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
