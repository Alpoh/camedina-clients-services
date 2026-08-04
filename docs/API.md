# API

## Status

No REST endpoints exist yet — there is no `spring-boot-starter-web` dependency, no controllers, and no
entities on the classpath yet (see `docs/ARCHITECTURE.md`). This document describes the conventions
future endpoints should follow, and should be updated with a real endpoint reference as they're built.

## Conventions (for endpoints as they're added)

- **Base path:** `/api/v1/...` — version the API from the start via the URL path.
- **Content type:** `application/json` for all request/response bodies.
- **Request/response shapes:** immutable `record` DTOs, validated with Bean Validation
  (`@NotBlank`, `@Email`, etc.) and `@Valid` on controller method parameters.
- **Errors:** [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `ProblemDetail` responses via Spring's
  built-in support, produced by a `@ControllerAdvice` — not a custom error-body shape. Example shape:
  ```json
  {
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "email: must be a well-formed email address",
    "instance": "/api/v1/clients"
  }
  ```
- **Pagination:** prefer Spring Data's `Pageable`/`Page<T>` for list endpoints once they exist, exposed
  via standard `page`/`size`/`sort` query params.
- **IDs:** resource IDs are opaque in URLs (e.g. `/api/v1/clients/{id}`); don't leak database-internal
  details beyond the primary key.

## Endpoints

_None implemented yet._

| Method | Path | Description | Status |
|--------|------|-------------|--------|
| —      | —    | —           | —      |
