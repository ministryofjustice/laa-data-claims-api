CREATE TABLE provider_offices (
    id                  UUID PRIMARY KEY,
    account_number      TEXT NOT NULL UNIQUE,
    created_by_user_id  UUID NOT NULL,
    created_on          TIMESTAMPTZ NOT NULL,
    modified_by_user_id UUID,
    modified_on         TIMESTAMPTZ
);