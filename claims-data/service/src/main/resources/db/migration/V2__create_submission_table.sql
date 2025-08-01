CREATE TABLE submission (
    id                      UUID PRIMARY KEY,
    bulk_submission_id      UUID NOT NULL REFERENCES bulk_submission(id),
    office_account_number   TEXT NOT NULL UNIQUE,
    submission_period       TEXT NOT NULL,
    area_of_law             TEXT NOT NULL,
    status                  TEXT NOT NULL CHECK (status IN ('READY_FOR_VALIDATION', 'VALIDATION_IN_PROGRESS', 'VALIDATION_SUCCEEDED', 'VALIDATION_FAILED', 'REPLACED')) NOT NULL,
    schedule_number         TEXT,
    previous_submission_id  UUID,
    is_nil_submission       BOOLEAN,
    number_of_claims        INTEGER,
    error_messages          TEXT,
    created_by_user_id      TEXT NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    updated_by_user_id      TEXT,
    updated_on              TIMESTAMPTZ
);
CREATE INDEX idx_submission_bulk_submission_id ON submission(bulk_submission_id);
