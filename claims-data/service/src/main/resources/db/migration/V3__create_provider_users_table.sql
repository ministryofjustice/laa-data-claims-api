CREATE TABLE provider_users (
    id                      UUID PRIMARY KEY,
    provider_office_id      UUID NOT NULL REFERENCES provider_offices(id),
    username                TEXT NOT NULL,
    created_by_user_id      TEXT NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    modified_by_user_id     TEXT,
    modified_on             TIMESTAMPTZ
);
CREATE INDEX idx_provider_users_provider_office_id ON provider_users(provider_office_id);