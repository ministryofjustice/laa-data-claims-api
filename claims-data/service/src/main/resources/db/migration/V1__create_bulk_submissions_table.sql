CREATE TABLE bulk_submissions (
    id                      UUID PRIMARY KEY,
    data                    JSONB NOT NULL,
    status                  TEXT NOT NULL CHECK (status IN ('READY_FOR_VALIDATION', 'VALIDATION_IN_PROGRESS', 'VALIDATION_COMPLETE, VALIDATION_FAILED', 'REPLACED')),
    error_code              TEXT,
    error_description       TEXT,
    created_by_user_id      UUID NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    modified_by_user_id     UUID,
    modified_on             TIMESTAMPTZ
);