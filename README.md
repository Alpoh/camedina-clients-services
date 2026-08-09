# clients-service

A clients management backend built with Spring Boot 4 / Java 26, as a portfolio project. It exposes a
REST API to manage clients and their phones, addresses, and projects, backed by PostgreSQL with
Flyway-owned schema migrations.

## Features

- **Auth** — self-issued JWT authentication (`POST /api/v1/auth/register`, `POST /api/v1/auth/login`);
  every other endpoint requires a bearer token.
- **Clients** — full CRUD, unique email, paged/sortable listing.
- **Phones & addresses** — independently managed sub-resources per client, each enforcing "at most one
  primary" via service logic and a DB partial unique index.
- **Projects** — sub-resource per client with a fixed status enum (`planning`, `in_progress`, `blocked`,
  `review`, `done`), matching an external admin/portal frontend's existing wire contract.
- **Validation & errors** — Bean Validation at the boundary, RFC 7807 `ProblemDetail` error responses,
  no leaked stack traces.
- **API docs** — live OpenAPI 3.1 spec + Swagger UI, generated from the controllers/DTOs (no
  hand-written spec).
- **Health/metrics** — Spring Boot Actuator (`/actuator/health`, `/actuator/info`,
  `/actuator/metrics`), backing the Docker image's `HEALTHCHECK`.
- **Dockerized** — multi-stage, Alpine-based image; local dev Postgres via Docker Compose,
  auto-wired by `spring-boot-docker-compose`.

## Tech stack

| Concern     | Choice                                                                 |
|-------------|-------------------------------------------------------------------------|
| Language    | Java 26                                                                  |
| Framework   | Spring Boot 4.1.0                                                        |
| Build       | Maven (wrapper-pinned, `./mvnw`)                                         |
| Web         | Spring MVC (`spring-boot-starter-web`), embedded Tomcat                  |
| Persistence | Spring Data JPA + Hibernate, PostgreSQL                                  |
| Migrations  | Flyway                                                                   |
| API docs    | `springdoc-openapi-starter-webmvc-ui` (OpenAPI 3.1 + Swagger UI)         |
| Auth        | `spring-boot-starter-security` + self-issued JWTs (`io.jsonwebtoken:jjwt-*`) |
| Local infra | Docker Compose (`compose.yaml`), auto-wired by `spring-boot-docker-compose` |
| Packaging   | Multi-stage `Dockerfile` (Eclipse Temurin 26, Alpine)                    |

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design rationale and package layout.

## Getting started

**Prerequisites:** JDK 26, and Docker running locally (the app's Postgres dependency starts
automatically via `spring-boot-docker-compose`).

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Interactive API docs are at
[`/swagger-ui/index.html`](http://localhost:8080/swagger-ui/index.html) — like every other endpoint
except `/api/v1/auth/**` and `/actuator/health`, it now requires a bearer token:

```bash
# register (or POST /api/v1/auth/login if you already have an account) — returns {"token": "..."}
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"a-password-at-least-8-chars"}'

# use the token on every other request
curl http://localhost:8080/api/v1/clients -H "Authorization: Bearer <token>"
```

```bash
./mvnw test      # run the test suite (starts Postgres via Docker Compose)
./mvnw verify     # tests + any bound verification
```

See [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) for the full command reference and code conventions.

## Running with Docker

```bash
docker build -t clients-service .
docker compose up -d   # start Postgres standalone
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:<port>/clients-service \
  -e SPRING_DATASOURCE_USERNAME=clients-service \
  -e SPRING_DATASOURCE_PASSWORD=clients-service \
  clients-service
```

The built image ships with no datasource configuration baked in — it must be supplied at runtime.

## Documentation

- [`docs/PLAN.md`](docs/PLAN.md) — what's done and proposed next steps
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — tech stack and design decisions
- [`docs/API.md`](docs/API.md) — REST API conventions and endpoint reference
- [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) — build/test/run commands and code conventions
- [`docs/CHANGELOG.md`](docs/CHANGELOG.md) — change history

## Status

Portfolio/learning project, under active development. See
[`docs/PLAN.md`](docs/PLAN.md) for the roadmap.
