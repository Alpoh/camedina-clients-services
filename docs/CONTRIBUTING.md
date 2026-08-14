# Contributing

## Prerequisites

- **JDK 26.** The system default `java` may still point at an older JDK
  (`update-alternatives --list java`). If a JDK 26 isn't on your `PATH`/`JAVA_HOME`, point it at one
  explicitly for Maven commands, e.g.:
  ```bash
  JAVA_HOME=/path/to/jdk-26 ./mvnw compile
  ```
- **Docker**, running and reachable from the shell. `compose.yaml`'s Postgres service is started
  automatically by `spring-boot-docker-compose` for both `spring-boot:run` and `./mvnw test` — without
  Docker, the app (and most tests) won't be able to obtain a `DataSource` and will fail to start.

Always use the Maven wrapper (`./mvnw`), not a system `mvn`, so builds use the version pinned in
`.mvn/wrapper`.

## Building & testing

```bash
./mvnw compile                          # compile
./mvnw test                             # run all tests (starts Postgres via Docker Compose)
./mvnw test -Dtest=ClassName            # run a single test class
./mvnw test -Dtest=ClassName#methodName # run a single test method
./mvnw verify                           # tests + any bound verification
./mvnw spring-boot:run                  # run the app locally (starts compose.yaml services first)
./mvnw clean package                    # build the executable jar (target/*.jar)
```

The first `test` or `spring-boot:run` will pull the `postgres:17.2-alpine` image, so expect it to be slower
than subsequent runs.

To browse Swagger UI (`/swagger-ui/index.html`) or `/v3/api-docs` without a bearer token locally, run
with the `local` profile active, e.g. `SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`. Every other
endpoint still requires auth even under `local` — see `docs/API.md`. Never set this profile in a deployed
environment.

`./mvnw test` also produces a JaCoCo coverage report at `target/site/jacoco/index.html` — open it in a
browser to see line/branch coverage per class. JUnit 5, AssertJ, and Mockito come from
`spring-boot-starter-test` already on the classpath; no extra test dependencies are needed to write
unit or slice tests.

## Running in Docker

```bash
docker build -t clients-service .   # build the app image
docker compose up -d                # start Postgres standalone, e.g. to run the image manually
```

The built image expects `spring.datasource.*` (or `SPRING_DATASOURCE_*` env vars) to be supplied at run
time — it has none baked in. See `docs/ARCHITECTURE.md` for how the image is built.

## Code conventions

- **Package-by-feature**, not package-by-layer — see `docs/ARCHITECTURE.md`.
- **Records for DTOs/value objects**, not Lombok `@Data` classes.
- **Constructor injection only** — no field `@Autowired`. `@RequiredArgsConstructor` on `final` fields is
  fine.
- **Avoid `@Data` on JPA entities** — write `equals`/`hashCode` on the ID only, if needed.
- **Bean validation at the boundary** (`@Valid` on controller params), mapped to `ProblemDetail`
  responses rather than hand-checked nulls in service code.
- No linter/formatter is configured yet (no Checkstyle/Spotless) — match the existing style by eye.

The full list of conventions (including ones aimed specifically at AI coding assistants working in this
repo) lives in `CLAUDE.md` at the project root.

## Commits & PRs

- Keep commits scoped to one logical change; write commit messages that explain *why*, not just *what*.
- Run `./mvnw test` before opening a PR — CI (`.github/workflows/ci-cd.yml`) re-runs `./mvnw verify` on
  every push/PR against `main` and is the actual gate, but catching failures locally first saves a round
  trip.
- On merge to `main`, CI also builds and pushes the Docker image to GHCR
  (`ghcr.io/alpoh/camedina-clients-services`) — no separate release step needed.
- There's no issue tracker or PR template set up yet; a short PR description of the change and rationale
  is enough.
