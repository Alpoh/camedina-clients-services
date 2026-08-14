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
| API docs        | `springdoc-openapi-starter-webmvc-ui` — live OpenAPI 3.1 + Swagger UI, generated from code |
| Health/metrics  | `spring-boot-starter-actuator` — `/actuator/health`, `/actuator/info`, `/actuator/metrics` |
| Auth            | `spring-boot-starter-security` + self-issued JWTs (`io.jsonwebtoken:jjwt-*`) — no external identity provider |
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
    Client.java                 (JPA entity)      Phone.java / Address.java / Project.java
    ClientRepository.java                          {Phone,Address,Project}Repository.java
    ClientService.java                             {Phone,Address,Project}Service.java
    ClientController.java                          {Phone,Address,Project}Controller.java
    ClientRequest.java / ClientResponse.java (record DTOs, one pair each per resource)
    ProjectStatus.java           # enum, Jackson @JsonValue/@JsonCreator maps to lowercase wire values
  auth/                         # second feature package — self-issued JWT auth
    User.java (JPA entity) / UserRepository.java
    RegisterRequest.java / LoginRequest.java / AuthResponse.java (record DTOs)
    AuthService.java / AuthController.java
    JwtProperties.java           # @ConfigurationProperties(prefix = "security.jwt")
    JwtService.java               # issues/validates HS256 tokens
    UserDetailsServiceImpl.java / JwtAuthenticationFilter.java / RestAuthenticationEntryPoint.java
    SecurityConfig.java          # SecurityFilterChain; wires the filter/entry point above as @Beans
  common/                       # shared across feature packages
    AuditableEntity.java         # @MappedSuperclass: createdAt/updatedAt via @PrePersist/@PreUpdate
    NotFoundException.java / ConflictException.java / GlobalExceptionHandler.java (@RestControllerAdvice)
```
`Phone`/`Address`/`Project` are independent sub-resources scoped by a plain `client_id` FK column — not
a bidirectional JPA relationship on `Client`. All three live in the `client` package alongside `Client`
itself, not in separate feature packages of their own — same reasoning for `Project` as for
`Phone`/`Address`, even though `Project` has more of an independent lifecycle than a pure attribute
would. `NotFoundException`/`ConflictException`/`GlobalExceptionHandler`/`AuditableEntity` used to live in
`client/`, back when it was the only feature package; `auth` becoming a second, genuinely independent
vertical triggered the planned hoist into `common/`.

## Persistence

- `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime) are on the classpath.
- Five entities so far: `Client`, `Phone`, `Address`, `Project` (`client` package), `User` (`auth`
  package) — see [Package layout](#package-layout).
- **Schema migrations own the schema, Hibernate only validates.** `spring-boot-starter-flyway` +
  `flyway-database-postgresql` are on the runtime classpath, and `spring.jpa.hibernate.ddl-auto=validate`
  is set — Hibernate never generates DDL; it just checks entities against whatever Flyway has applied.
  Flyway runs automatically on startup (`spring-boot:run` and `./mvnw test`) against migrations on
  `classpath:db/migration` (default location), auto-configured for the same Postgres instance
  `spring-boot-docker-compose` wires up. `V1__create_client_tables.sql` creates `clients`,
  `client_phones`, `client_addresses` — including a partial unique index per phones/addresses table
  (`WHERE is_primary`) enforcing at most one primary row per client as defense-in-depth alongside the
  service-layer logic. `V2__create_projects_table.sql` creates `projects` (FK to `clients`,
  `ON DELETE CASCADE`, no primary-flag concept needed). `V3__create_users_table.sql` creates `users`
  (unique email, BCrypt password hash).
- Local dev and tests get a real Postgres instance for free: `compose.yaml` defines a
  `postgres:17.2-alpine` service, and `spring-boot-docker-compose` starts it and injects
  `spring.datasource.*` automatically for
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

## Security

- Self-issued JWT authentication (`auth` package) — no external identity provider (Keycloak/Auth0/etc.).
  `POST /api/v1/auth/register` and `POST /api/v1/auth/login` are the only `permitAll` routes (besides
  `/actuator/health`); every other endpoint requires `Authorization: Bearer <token>`, enforced by
  `SecurityConfig`'s `SecurityFilterChain` (`SessionCreationPolicy.STATELESS`, CSRF disabled — no
  cookies, nothing to forge).
- `JwtService` issues/validates HS256 tokens signed with `security.jwt.secret` (a
  `@ConfigurationProperties` record, `JwtProperties`); `security.jwt.expiration` defaults to `PT1H`. The
  secret follows the same env-var-override pattern as `spring.datasource.*` (see `CLAUDE.md`'s Secrets
  note) — a labeled dev-only default in `application.properties`, overridden via `SECURITY_JWT_SECRET`
  in any real deployment.
- `JwtAuthenticationFilter` and `RestAuthenticationEntryPoint` are wired as `@Bean`s inside
  `SecurityConfig`, not `@Component`-scanned: a component-scanned `Filter` bean gets swept into every
  `@WebMvcTest` slice regardless of `addFilters`, and fails to construct there for lack of
  `JwtService`/`UserDetailsServiceImpl` in that minimal context — found by running the suite after
  adding Spring Security, not by inspection.
- No roles/authorities exist yet, just authenticated-or-not — add a roles concept only once an endpoint
  actually needs to distinguish callers.

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
  `http://localhost:8080/actuator/health` (no `bash`/`curl` available to do a `/dev/tcp` trick or a curl
  check). `spring-boot-starter-actuator`'s health endpoint includes the JPA/Datasource health indicator,
  so the healthcheck still verifies DB connectivity, not just "Tomcat is listening" — it previously
  reused the business `/api/v1/clients` route for the same reason, before actuator existed.

## Deployment topology

- `clients-service` is not internet-facing. It runs as an ECS task registered in a private Cloud Map DNS
  namespace (`clients-service.dev.internal`, port 8080), provisioned by `ecs-cluster.yaml` in the
  separate `clients-infra` repo (infra-as-code lives there, not in this repo — same as the ECS
  cluster/service referenced in CI/CD, see `CLAUDE.md`).
- The external admin/portal frontend (`clients-front`, also a separate repo) is the only component
  behind the public ALB. Its ECS task is given `BACKEND_API_URL` pointing at the Cloud Map DNS name above
  and calls `clients-service` **server-side** — the browser never talks to `clients-service` directly.
- Task-to-task reachability is enforced by **security groups**, not a Docker bridge network:
  `clients-front`'s security group is allowed to reach `clients-service`'s security group on port 8080,
  and nothing else can. The nearest equivalent to "same Docker network" in this topology is same VPC +
  private subnets + Cloud Map service discovery + that security group rule — there's no literal shared
  network namespace the way `compose.yaml`'s services share one locally.
- **Consequence for CORS**: because `clients-front` calls `clients-service` server-side rather than the
  browser calling it directly, cross-origin requests never reach `clients-service` — CORS is a
  browser-enforced restriction, and no browser traffic hits this API. No `CorsConfigurationSource` bean
  is needed here as a result; see the CORS note in `CLAUDE.md`.
- **Consequence for auth**: it's `clients-front`'s server-side code that holds/attaches the JWT
  (`Authorization: Bearer <token>`) when calling `clients-service`, not browser-side JS — there is no
  browser-side token-management concern for this API to account for.

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
- `@WebMvcTest` controller tests carry `@AutoConfigureMockMvc(addFilters = false)` so they test
  controller/serialization/validation logic without the real security filter chain in the way — security
  behavior itself (401 without a token, 200 with one, `/actuator/health` staying open) is covered by one
  dedicated `SecurityIntegrationTest` (`@SpringBootTest`) instead.

## Current status / roadmap

Two feature verticals are implemented end-to-end: `client` (`Client` plus independently-managed `Phone`/
`Address`/`Project` sub-resources, full CRUD REST API) and `auth` (self-issued JWT authentication —
register/login, every other endpoint requires a bearer token). Flyway-owned schema, `ProblemDetail`
error handling, OpenAPI/Swagger docs (now behind auth), Actuator health/metrics (only `/actuator/health`
stays open), and test coverage across all layers. See `docs/PLAN.md` for what's next: a CI pipeline,
virtual threads, and roles/authorities once an endpoint needs to distinguish callers.
