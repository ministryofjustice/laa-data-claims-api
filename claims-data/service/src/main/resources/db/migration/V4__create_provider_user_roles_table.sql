CREATE TABLE provider_user_roles (
    id                  UUID PRIMARY KEY,
    provider_user_id    UUID NOT NULL REFERENCES provider_users(id),
    role_name           TEXT NOT NULL,
    created_by_user_id  TEXT NOT NULL,
    created_on          TIMESTAMPTZ NOT NULL,
    modified_by_user_id TEXT,
    modified_on         TIMESTAMPTZ
);
CREATE INDEX idx_provider_user_roles_provider_user_id ON provider_user_roles(provider_user_id);