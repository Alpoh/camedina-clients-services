CREATE TABLE clients (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_clients_email UNIQUE (email)
);

CREATE TABLE client_phones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id   UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    number      VARCHAR(50) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_client_phones_client_id ON client_phones (client_id);
CREATE UNIQUE INDEX uq_client_phones_primary ON client_phones (client_id) WHERE is_primary;

CREATE TABLE client_addresses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id    UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    street       VARCHAR(255) NOT NULL,
    city         VARCHAR(255) NOT NULL,
    state        VARCHAR(255),
    postal_code  VARCHAR(20) NOT NULL,
    country      VARCHAR(2) NOT NULL,
    type         VARCHAR(20) NOT NULL,
    is_primary   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_client_addresses_client_id ON client_addresses (client_id);
CREATE UNIQUE INDEX uq_client_addresses_primary ON client_addresses (client_id) WHERE is_primary;
