# API

## Status

The `client` feature vertical is implemented: `Client` plus its `Phone` and `Address` sub-resources
(see `docs/ARCHITECTURE.md`). This document describes the conventions endpoints follow, and should be
kept in sync with the real endpoint reference below as more are added.

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

### Clients

| Method | Path                    | Description                          | Status |
|--------|-------------------------|---------------------------------------|--------|
| POST   | `/api/v1/clients`       | Create a client                       | 201 + `Location`, 400, 409 |
| GET    | `/api/v1/clients/{id}`  | Get a client by id                    | 200, 404 |
| GET    | `/api/v1/clients`       | List clients (paged, sortable)        | 200 (`Page<ClientResponse>`) |
| PUT    | `/api/v1/clients/{id}`  | Replace a client's name/email         | 200, 400, 404, 409 |
| DELETE | `/api/v1/clients/{id}`  | Delete a client (cascades phones/addresses) | 204, 404 |

### Phones (sub-resource of a client)

| Method | Path                                            | Description                        | Status |
|--------|--------------------------------------------------|-------------------------------------|--------|
| POST   | `/api/v1/clients/{clientId}/phones`               | Add a phone                        | 201 + `Location`, 400, 404 |
| GET    | `/api/v1/clients/{clientId}/phones/{phoneId}`     | Get a phone by id                  | 200, 404 |
| GET    | `/api/v1/clients/{clientId}/phones`               | List a client's phones (paged, sortable) | 200 (`Page<PhoneResponse>`) |
| PUT    | `/api/v1/clients/{clientId}/phones/{phoneId}`     | Replace a phone                    | 200, 400, 404 |
| DELETE | `/api/v1/clients/{clientId}/phones/{phoneId}`     | Delete a phone                     | 204, 404 |

### Addresses (sub-resource of a client)

| Method | Path                                                | Description                        | Status |
|--------|-------------------------------------------------------|-------------------------------------|--------|
| POST   | `/api/v1/clients/{clientId}/addresses`                 | Add an address                     | 201 + `Location`, 400, 404 |
| GET    | `/api/v1/clients/{clientId}/addresses/{addressId}`     | Get an address by id                | 200, 404 |
| GET    | `/api/v1/clients/{clientId}/addresses`                 | List a client's addresses (paged, sortable) | 200 (`Page<AddressResponse>`) |
| PUT    | `/api/v1/clients/{clientId}/addresses/{addressId}`     | Replace an address                  | 200, 400, 404 |
| DELETE | `/api/v1/clients/{clientId}/addresses/{addressId}`     | Delete an address                   | 204, 404 |

Phones and addresses each carry a `primary` flag: a client always has exactly one primary phone/address
once it has at least one (enforced by the service layer on create/update, backed by a DB partial unique
index). A phone/address request for a client that doesn't exist, or a phone/address id that belongs to a
*different* client than the one in the path, both return 404.
