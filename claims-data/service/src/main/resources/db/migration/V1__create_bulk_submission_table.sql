CREATE TABLE bulk_submission (
    id                      UUID NOT NULL,
    data                    JSONB NOT NULL,
    status                  TEXT NOT NULL,
    error_code              TEXT,
    error_description       TEXT,
    created_by_user_id      TEXT NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    updated_by_user_id      TEXT,
    updated_on              TIMESTAMPTZ,

    CONSTRAINT pk_bulk_submission PRIMARY KEY (id),
    CONSTRAINT chk_bulk_submission_status CHECK (status IN ('READY_FOR_PARSING', 'PARSING_COMPLETED', 'PARSING_FAILED', 'VALIDATION_FAILED', 'REPLACED'))
);