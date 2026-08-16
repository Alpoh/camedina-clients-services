# Implementation plan — `clients-service`

The Spring Boot workstream for the cross-repo review in
`clients-infra/docs/ARCHITECTURE_IMPROVEMENTS.md` (`~/IdeaProjects/clients-infra`). Gap IDs
(`G1`…`G21`) and phase numbers refer to that document. Sibling plans live in
`clients-infra/docs/IMPLEMENTATION_PLAN.md` and `clients-front/docs/IMPLEMENTATION_PLAN.md`.

This file is about *new architecture*. `docs/PLAN.md` remains the working status doc for this repo's
own roadmap, `docs/ARCHITECTURE.md` describes the service as built, and `CLAUDE.md` stays the single
source of truth for conventions. Everything below inherits those conventions: package-by-feature,
`record` DTOs validated at the boundary, constructor injection, `@ConfigurationProperties` records
over scattered `@Value`, Flyway owns DDL, RFC 7807 error bodies, and no internals leaked in
responses.

---

## Phase 0 — Correctness (~half a day)

### 0.1 Liveness/readiness probe groups (G5)

ECS on Fargate ignores the Dockerfile `HEALTHCHECK`; the infra plan adds a
`ContainerDefinition.HealthCheck` pointing at `/actuator/health/readiness`, which doesn't exist yet.

`application.properties`:

```properties
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.readiness.include=readinessState,db
management.endpoint.health.group.liveness.include=livenessState
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

`SecurityConfig` currently permits `/actuator/health`; widen that to `/actuator/health/**` so the
probe paths stay open, and keep everything else authenticated.

The distinction matters: **liveness** failing should restart the task, **readiness** failing should
only stop routing traffic to it. Including `db` in readiness but not liveness means a brief RDS
blip drains the task instead of killing it.

### 0.2 Fail fast on a default JWT secret (G1)

The infra fix injects `SECURITY_JWT_SECRET`, but nothing today would have caught its absence. Add a
guard in `JwtProperties` or an `@PostConstruct` check that throws on startup if the active profile
is not `local`/`test` and the secret still equals the dev-only default. A service that refuses to
start beats a service that silently signs tokens with a public secret.

While in there: enforce a minimum key length (HS256 wants ≥ 256 bits) and reject anything shorter.

### 0.3 Graceful shutdown

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=25s
```

ECS sends `SIGTERM` then waits 30 s. Without this, in-flight requests are cut on every deploy — and
once the worker exists (Phase 3), an ungraceful shutdown mid-handler means a message redelivery
that a non-idempotent consumer would double-process.

---

## Phase 2 — Identity consolidation (~2 days)

Implements ADR-004. Closes G4 and the backend half of G7.

Today the backend `User` knows only email + BCrypt password, while `clients-front`'s
`lib/mock-data/users.ts` is the only source of role, display name and `clientId`. A real backend
account cannot log in unless a hardcoded frontend entry matches its email. That split is the
system's biggest domain-level architecture gap.

### 2.1 Extend `User`

`V4__add_user_profile_fields.sql`:

```sql
ALTER TABLE users
  ADD COLUMN role         VARCHAR(20)  NOT NULL DEFAULT 'CLIENT',
  ADD COLUMN display_name VARCHAR(120),
  ADD COLUMN client_id    UUID         REFERENCES clients(id) ON DELETE SET NULL;

CREATE INDEX idx_users_client_id ON users(client_id);
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN','CLIENT'));
```

A `Role` enum (`ADMIN`, `CLIENT`) in the `auth` package, mapped with `@Enumerated(EnumType.STRING)`,
and Jackson `@JsonValue`/`@JsonCreator` to lowercase wire values — the same pattern `ProjectStatus`
already uses, so the frontend's existing `"admin"`/`"client"` strings keep working unchanged.

Constraint worth deciding explicitly: a `CLIENT` user must have a `client_id`; an `ADMIN` must not.
Enforce it in `AuthService` and, if you want defence in depth, as a `CHECK` constraint.

### 2.2 Claims in the JWT

`JwtService` adds `role`, `name` and `clientId` claims. `JwtAuthenticationFilter` builds the
`Authentication` with `SimpleGrantedAuthority("ROLE_" + role)` from the claim rather than reloading
the user on every request — that's the point of putting it in the token.

Keep `UserDetailsServiceImpl` for the login path only.

### 2.3 Authorisation rules

Enable `@EnableMethodSecurity` and annotate:

- `@PreAuthorize("hasRole('ADMIN')")` on client/phone/address create/update/delete.
- `@PreAuthorize("hasRole('ADMIN') or #clientId == authentication.principal.clientId")` on reads
  scoped to a client — this is what makes the portal genuinely multi-tenant instead of relying on
  the frontend to not show the wrong data.
- A `403` `ProblemDetail` in `GlobalExceptionHandler` for `AccessDeniedException`, matching the
  existing 401 handling (generic detail, no resource enumeration).

**This is the highest-value security change in the whole plan.** Today any authenticated user can
read any client's data by guessing a UUID; the frontend's role check is cosmetic.

Test it: extend `SecurityIntegrationTest` with an admin-vs-client matrix — client reading their own
projects (200), client reading another client's (403), admin reading any (200), anonymous (401).

### 2.4 `/api/v1/auth/me`

A small endpoint returning `{ id, email, role, name, clientId }` for the current principal. It lets
`clients-front` build its session from the backend instead of from mock data, and it's the seam that
lets the mock table be deleted.

### 2.5 Refresh tokens (G7)

The frontend session cookie lasts 7 days; the backend JWT lasts 1 hour. Between them a user gets a
silent `401` mid-session. Options, cheapest first:

1. **Lengthen the access token to ~8 h and shorten the session cookie to match.** One line of
   config, no new state; weakens revocation, which barely exists anyway.
2. **Refresh tokens** (recommended): a `refresh_tokens` table (opaque random token, hashed, user id,
   expiry, revoked flag), `POST /api/v1/auth/refresh`, rotation on use, and revocation on logout.
   ~200 lines and a migration, and it gives a real `POST /api/v1/auth/logout`, which the system
   currently cannot implement.
3. **A full OAuth2 authorization server** — out of proportion here.

Take option 2. It's the answer to "how do you revoke a JWT?", which comes up in every interview
that touches JWTs, and it's better to have built it than to have an opinion about it.

### 2.6 Optional: RS256 + JWKS

Only needed if Phase 5's API Gateway JWT authorizer happens — it validates against a JWKS endpoint
and cannot verify an HS256 shared secret. Migrating means an RSA keypair in Secrets Manager, a
`/.well-known/jwks.json` endpoint, and a `kid` header for rotation. Defer until Phase 5 is actually
being built; note the dependency so it isn't a surprise.

---

## Phase 3 — Event backbone (~3 days)

Implements ADR-002 and ADR-003.

### 3.1 Dependencies

```xml
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter-sns</artifactId>
</dependency>
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter-sqs</artifactId>
</dependency>
```

with `spring-cloud-aws-dependencies` in `dependencyManagement`. **Check compatibility first** —
Spring Cloud AWS tracks Spring Boot releases and this project is on Boot 4.1, which is ahead of most
of the ecosystem (springdoc already needed a version predating Boot 4.1). If the starter isn't ready,
fall back to the plain AWS SDK v2 (`software.amazon.awssdk:sns` / `:sqs`) with a hand-rolled
`@Scheduled` poller — more code, zero compatibility risk, and arguably clearer for a reader.

Local dev: add LocalStack to `compose.yaml` (`localstack/localstack`, services `sns,sqs`) so
`spring-boot-docker-compose` wires it the same way it already wires Postgres, and tests exercise
real SNS/SQS semantics rather than mocks. That fits this repo's existing "test against the real
thing" stance.

### 3.2 New package: `events/`

Package-by-feature, as `CLAUDE.md` requires — this is a genuine third vertical, not a layer:

```
events/
  DomainEvent.java            # sealed interface; eventId, occurredAt, eventType, correlationId
  ProjectStatusChanged.java   # record implements DomainEvent
  UserRegistered.java
  BulkImportRequested.java
  OutboxEvent.java            # JPA entity for the outbox table
  OutboxRepository.java
  OutboxRecorder.java         # called from service methods, inside the business transaction
  OutboxPublisher.java        # @Scheduled poller → SNS
  OutboxProperties.java       # @ConfigurationProperties(prefix = "events.outbox")
```

A `sealed interface DomainEvent` permitting the concrete records gives exhaustive `switch` in the
worker — a nice use of the Java 26 baseline this project already targets.

### 3.3 Outbox table

`V5__create_outbox_events.sql`:

```sql
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     TEXT
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
```

The partial index is the same defence-in-depth instinct as `V1`'s partial unique indexes on primary
phones/addresses — the poller only ever scans unpublished rows.

### 3.4 The two halves

**Recording** — `ProjectService.updateStatus(...)` calls `outboxRecorder.record(new
ProjectStatusChanged(...))`, which does a plain `INSERT` in the caller's transaction. If the
business write rolls back, so does the event. No AWS call happens on the request path, so the
endpoint's latency and failure modes are unchanged.

**Publishing** — `OutboxPublisher`, `@Scheduled(fixedDelayString = "${events.outbox.poll-interval}")`,
selects unpublished rows `ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED`, publishes each to
SNS with an `eventType` message attribute (the SNS filter policies key off it), then stamps
`published_at`. `SKIP LOCKED` is what makes running two backend tasks safe without a leader election.

Failures increment `attempts` and record `last_error`; after N attempts, log at `ERROR` (the infra
plan's log metric filter turns that into an alarm). Don't delete published rows immediately — a
nightly `@Scheduled` cleanup of rows published more than 7 days ago keeps the table small while
leaving a short audit window.

### 3.5 The worker side

Same repo, same image, different profile — `SPRING_PROFILES_ACTIVE=worker`, deployed as the separate
`clients-worker` ECS service the infra plan defines.

```
worker/
  NotificationListener.java   # @SqsListener("${events.queues.notifications}")
  JobListener.java            # @SqsListener("${events.queues.jobs}")
  ProcessedEventRepository.java
```

Guard both listeners with `@Profile("worker")`, and guard the web layer with `@Profile("!worker")`
if the worker shouldn't serve HTTP — though keeping actuator up on the worker is worth it for the
ECS health check, so the cleanest split is `application-worker.properties` disabling the business
controllers rather than the whole web stack.

**Idempotency is not optional.** SQS standard queues are at-least-once. `V6__create_processed_events.sql`
creates `processed_events (event_id UUID PRIMARY KEY, processed_at TIMESTAMPTZ)`; every handler does
an `INSERT ... ON CONFLICT DO NOTHING` first and returns early if the row already existed. The
primary key does the work; no distributed lock needed.

Handlers must finish within the queue's `VisibilityTimeout` (60 s per the infra plan) or the message
is redelivered while still being processed. For genuinely long jobs, extend visibility via a
heartbeat rather than raising the timeout for everything.

### 3.6 Async job endpoints

The concrete "decouple front and back" feature, since CRUD legitimately stays synchronous:

- `POST /api/v1/imports` (multipart CSV) → validate the file shape, persist an `import_jobs` row
  with status `PENDING`, record a `BulkImportRequested` outbox event, return **`202 Accepted`** with
  a `Location: /api/v1/imports/{id}` header.
- `GET /api/v1/imports/{id}` → `{ id, status, totalRows, processedRows, failedRows, errors[] }`.
- The worker consumes the event, streams the CSV from S3 (upload it in the POST via a presigned URL
  or straight through the backend), creates clients in batches, and updates the job row as it goes.
- Same shape for `POST /api/v1/projects/{id}/report` → generates a PDF to S3 → job row carries a
  presigned download URL.

This is the pattern worth being able to draw on a whiteboard: submit → 202 + job id → poll → result.

### 3.7 Notifications

`NotificationListener` handles `ProjectStatusChanged` and `UserRegistered` by sending email through
SES (`software.amazon.awssdk:ses`). SES starts in sandbox mode — only verified recipients — which is
fine for a portfolio and worth a line in the README so it doesn't look broken. A Thymeleaf template
for the email body keeps it out of Java string concatenation.

Also write an `activity_feed` row per event so the portal has something visible to render; a feature
the user can *see* is worth more than one they have to take on faith.

---

## Phase 4 — Observability (~2 days)

### 4.1 Correlation IDs (G10)

- An `OncePerRequestFilter` in `common/` that reads `X-Request-Id` (generating a UUID if absent),
  puts it in the SLF4J `MDC`, and echoes it on the response.
- The outbox carries `correlation_id` into the event payload; the worker restores it into the MDC
  before handling. One id follows a request from the browser through the BFF, the API, the queue and
  the worker.

### 4.2 JSON logging (G11)

Logback with `ch.qos.logback.classic.encoder.JsonEncoder` (or `logstash-logback-encoder`), enabled
only under the deployed profiles — keep human-readable logs locally. Include `correlationId`,
`userId`, `traceId`. CloudWatch Logs Insights can then query fields instead of regexing.

### 4.3 Tracing

`io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`, or the OTel Java agent added
to the Dockerfile's `ENTRYPOINT`. The ADOT sidecar from the infra plan receives on
`localhost:4317`. Automatic spans cover Tomcat, JDBC, `RestClient` and SQS, which is exactly the
path worth seeing on a service map.

### 4.4 Metrics

Actuator + Micrometer is already on the classpath. Add `micrometer-registry-cloudwatch2` (or scrape
`/actuator/prometheus`) and register a couple of business counters — `projects.status.changed`,
`outbox.published`, `outbox.failed`, `import.rows.processed`. Business metrics on a dashboard read
much better than CPU graphs alone.

---

## Ongoing / smaller items

| Item | Gap | Notes |
|---|---|---|
| Virtual threads (`spring.threads.virtual.enabled=true`) | — | Already item 3 in `docs/PLAN.md`. Phase 3 adds real I/O-bound work, so it finally has a reason. Benchmark before/after with the k6 test rather than just flipping it. |
| Optimistic locking | G20 | `@Version` on `Client`/`Project` + a `409` mapping for `OptimisticLockingFailureException` in `GlobalExceptionHandler`. Two admins editing one project currently silently lose a write. |
| Rate-limit `/auth/login` | G14 | Bucket4j, or a `failed_login_attempts` counter with exponential backoff per email. Complements the WAF rate rule (which is per-IP and can't see per-account patterns). |
| Least-privilege DB user | — | A Flyway migration creating `app_user` with DML-only grants; stop running the app as the RDS master user. |
| `ProblemDetail` `type` URIs | — | Currently `about:blank` by default; a stable `https://camedina.dev/errors/<slug>` per error type makes the API self-documenting. |
| Pagination defaults | — | Cap `Pageable` size (`spring.data.web.pageable.max-page-size=100`) so `?size=1000000` can't be used as a DoS. |
| Collapse the duplicate Docker build | — | Already item 2 in `docs/PLAN.md`; `ci-cd.yml` and `deploy.yml` both build the same image on every push to `main`. |
| Contract test | G12 | A CI step that boots the app, dumps `/v3/api-docs` to `docs/openapi.json`, and fails on an uncommitted diff. That file becomes the input to the frontend's generated client. |
| Testcontainers | — | The `spring-boot-docker-compose` approach works but couples tests to `compose.yaml`. Testcontainers would isolate them and make LocalStack setup per-test. Optional; the current approach is defensible and documented. |

---

## Sequencing

```
Phase 0 (0.5 d) ─┬─> Phase 2 (2 d) ──> Phase 3 (3 d) ──> Phase 4 (2 d)
                 └─> smaller items, any time
```

Phase 2 before Phase 3 because the events want a real `userId`/`role` on them, and because
`@PreAuthorize` is the higher-value change of the two. Phase 4 immediately after Phase 3, while the
async flow is fresh — a service map showing browser → BFF → API → SQS → worker is the single best
artifact this project can produce.

## How to update this doc

Same convention as `docs/PLAN.md`: strike finished items, move the detail into
`docs/ARCHITECTURE.md` and `docs/CHANGELOG.md` once it's how the service actually works, and keep
the gap IDs so this file stays aligned with `clients-infra/docs/ARCHITECTURE_IMPROVEMENTS.md`.
