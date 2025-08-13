CREATE TABLE submission (
    id                      UUID NOT NULL,
    bulk_submission_id      UUID NOT NULL,
    office_account_number   TEXT NOT NULL,
    submission_period       TEXT NOT NULL,
    area_of_law             TEXT NOT NULL,
    status                  TEXT NOT NULL,
    schedule_number         TEXT,
    previous_submission_id  UUID,
    is_nil_submission       BOOLEAN,
    number_of_claims        INTEGER,
    error_messages          TEXT,
    created_by_user_id      TEXT NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    updated_by_user_id      TEXT,
    updated_on              TIMESTAMPTZ,

    CONSTRAINT pk_submission PRIMARY KEY (id),
    CONSTRAINT fk_submission_bulk_submission_id FOREIGN KEY (bulk_submission_id) REFERENCES bulk_submission(id),
    CONSTRAINT chk_submission_status CHECK (status IN ('CREATED', 'READY_FOR_VALIDATION', 'VALIDATION_IN_PROGRESS', 'VALIDATION_SUCCEEDED', 'VALIDATION_FAILED', 'REPLACED'))
);
CREATE INDEX ix_submission_bulk_submission_id ON submission(bulk_submission_id);
