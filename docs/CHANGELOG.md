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
- `Dockerfile` `HEALTHCHECK`: BusyBox `wget --spider -q -T 3` against `/api/v1/clients` (Alpine has
  neither `bash` nor `curl`) — also exercises DB connectivity, not just Tomcat liveness. Verified against
  the compose Postgres via a manual `docker build` + `docker run` smoke test, including the full REST API
  working end-to-end from inside the container.
- `springdoc-openapi-starter-webmvc-ui:2.8.6` — live OpenAPI 3.1 spec at `/v3/api-docs` and Swagger UI at
  `/swagger-ui/index.html`, generated entirely from the existing controllers/DTOs/validation annotations.
  Verified end-to-end: all 6 controllers/15 routes and every request/response schema show up correctly.
- `Project`, as a `Client` sub-resource: `/api/v1/clients/{clientId}/projects`, mirroring the
  `Phone`/`Address` CRUD pattern (own table, `client_id` FK with `ON DELETE CASCADE`, no "primary"
  concept). Matches an existing external admin/portal frontend's mocked shape — a fixed `ProjectStatus`
  enum (`PLANNING`/`IN_PROGRESS`/`BLOCKED`/`REVIEW`/`DONE`) wire-mapped via Jackson
  `@JsonValue`/`@JsonCreator` to the frontend's lowercase-snake-case values (`planning`/`in_progress`/
  etc.), confirmed correct both at runtime and in the generated OpenAPI schema's enum values. Strictly
  single-client, no assignable staff (no `User`/auth concept exists yet).
  `db/migration/V2__create_projects_table.sql`.
- `GlobalExceptionHandler`: added `HttpMessageNotReadableException` (malformed JSON body, e.g. an
  invalid enum value — 400, fixed non-leaky detail) and a catch-all `Exception` handler (500, fixed
  detail, logs the real exception server-side via `@Slf4j`). Previously both fell through to Spring
  Boot's default error handling and could leak a raw stack trace to the client.
- `spring-boot-starter-actuator`: `/actuator/health` (with DB liveness via the JPA/Datasource health
  indicator), `/actuator/info`, `/actuator/metrics` exposed via
  `management.endpoints.web.exposure.include=health,info,metrics`; component `show-details` left at its
  `never` default (no auth yet to gate it behind).
- `README.md` — project overview, tech stack, getting-started commands, links to `docs/`.
- MIT `LICENSE`, also declared in `pom.xml`'s `<licenses>` block. The GitHub repo is now public.
- Second feature vertical: `auth` package, self-issued JWT authentication (`spring-boot-starter-security`
  + `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson:0.13.0`). `User` (email, BCrypt password hash),
  `db/migration/V3__create_users_table.sql`. `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
  (both `permitAll`) return `{"token": "..."}`; every other endpoint now requires
  `Authorization: Bearer <token>` — `SecurityConfig` permits only `/api/v1/auth/**` and
  `/actuator/health`, `anyRequest().authenticated()` for the rest, which also locks down Swagger UI/
  `/v3/api-docs` and `/actuator/info`/`/actuator/metrics` (no dev/staging profile split exists yet to
  scope that more precisely). `JwtService` issues/validates HS256 tokens
  (`security.jwt.secret`/`security.jwt.expiration`, a new `JwtProperties` `@ConfigurationProperties`
  record). `RestAuthenticationEntryPoint` (unauthenticated request to a protected endpoint) and a new
  `GlobalExceptionHandler.handleAuthentication` (`AuthenticationException`, e.g. bad login credentials)
  both return RFC 7807 `ProblemDetail` 401s with a generic "invalid credentials"/"authentication
  required" detail, not which part was wrong. No roles/authorities yet, just authenticated-or-not.
  Verified end-to-end via the automated suite and manually via `spring-boot:run` + `curl` (register,
  duplicate-email 409, login, bad-password 401, protected endpoint 401/200, `/actuator/health` open,
  Swagger now 401).
- `SecurityIntegrationTest` (`@SpringBootTest`) — exercises the real `SecurityFilterChain` end to end
  (unauthenticated 401, valid token 200, token for a deleted/unknown user 401, `/actuator/health` open).

### Changed
- `AuditableEntity`/`NotFoundException`/`ConflictException`/`GlobalExceptionHandler` moved from `client`
  to a new shared `common` package — `auth` becoming a second, genuinely independent feature vertical
  triggered the hoist `docs/PLAN.md` had flagged as the eventual trigger.
- Existing `@WebMvcTest` classes (`ClientControllerTest`, `PhoneControllerTest`, `AddressControllerTest`,
  `ProjectControllerTest`) gained `@AutoConfigureMockMvc(addFilters = false)` so they keep testing
  controller logic rather than the new security filter chain.
- Dockerfile `HEALTHCHECK` switched from the business `/api/v1/clients` route to `/actuator/health` now
  that `spring-boot-starter-actuator` is on the classpath.
- Both `Dockerfile` stages switched from Debian-based `eclipse-temurin:26-jdk`/`26-jre` to
  `26-jdk-alpine`/`26-jre-alpine` — ~40% smaller runtime image (~312MB vs. ~517MB). Required switching
  `addgroup`/`adduser` to BusyBox's short-flag syntax (`-S`/`-G`) instead of Debian shadow-utils' long
  flags.
- `compose.yaml`'s Postgres switched from `postgres:17.2` to `postgres:17.2-alpine` (~36% smaller:
  ~398MB vs. ~620MB); `pg_isready` healthcheck needed no changes.
- `ClientService`/`PhoneService`/`AddressService` and their controllers: `getById`/`getAll` renamed to
  `findById`/`findAll` — `get*` reads as a plain field accessor even though these take arguments, hit the
  DB, and can throw `NotFoundException`.
- Renamed group/artifact from `co.medina.portafolio:camedina-clients-service` to
  `co.medina.portfolio:clients-service` (fixing the `portafolio` typo and dropping the redundant
  `camedina-` prefix).
- Base package renamed from `co.medina.portafolio.camedinaclientsservice` to
  `co.medina.portfolio.clientsservice`.
- Test dependencies: `spring-boot-starter-test` replaced with `spring-boot-starter-webmvc-test` +
  `spring-boot-starter-data-jpa-test` (each transitively includes it). Spring Boot 4.1 split
  `@WebMvcTest`/`@DataJpaTest`/`@AutoConfigureTestDatabase` out of `spring-boot-test-autoconfigure` into
  these dedicated starters, and replaced `@MockBean`/`@SpyBean` with `@MockitoBean`/`@MockitoSpyBean`.

### Fixed
- `jacoco-maven-plugin` version pinned explicitly in `pom.xml` (`0.8.15`). It was previously unpinned
  and silently resolving to "latest release" at build time — not managed by `spring-boot-starter-parent`
  as the docs assumed — which Maven flags as a non-reproducible build.
- The three `getAll` controller methods' `Pageable` parameters now carry `@ParameterObject`
  (`org.springdoc.core.annotations`). Without it, springdoc rendered `page`/`size`/`sort` as one opaque
  object query parameter instead of three separate ones, and Swagger UI's array widget for the
  un-flattened `sort` field sent malformed requests (literally `sort=["ASC"]`) that 500'd with
  `InvalidDataAccessApiUsageException`. Found by actually exercising the Swagger UI, not just checking
  it loaded.

## 0.0.1-SNAPSHOT

Initial Spring Initializr scaffold: `co.medina.portafolio:camedina-clients-service`, Spring Boot 4.1.0,
Java 26, Lombok + `spring-boot-configuration-processor`, empty `@SpringBootApplication` class, no
business logic.
