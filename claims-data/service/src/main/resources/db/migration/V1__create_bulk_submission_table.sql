CREATE TABLE bulk_submission (
    id                      UUID PRIMARY KEY,
    data                    JSONB NOT NULL,
    status                  TEXT NOT NULL CHECK (status IN ('READY_FOR_VALIDATION', 'VALIDATION_IN_PROGRESS', 'VALIDATION_SUCCEEDED', 'VALIDATION_FAILED', 'REPLACED')),
    error_code              TEXT,
    error_description       TEXT,
    created_by_user_id      TEXT NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    updated_by_user_id      TEXT,
    updated_on              TIMESTAMPTZ
);