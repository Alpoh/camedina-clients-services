CREATE TABLE projects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id    UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(2000),
    status       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_projects_client_id ON projects (client_id);
