CREATE TABLE submissions (
    id                      UUID PRIMARY KEY,
    bulk_submission_id      UUID NOT NULL REFERENCES bulk_submissions(id),
    provider_office_id      UUID NOT NULL REFERENCES provider_offices(id),
    provider_user_id        UUID NOT NULL REFERENCES provider_users(id),
    submission_period       TEXT NOT NULL,
    area_of_law             TEXT NOT NULL,
    status                  TEXT NOT NULL CHECK (status IN ('READY_FOR_VALIDATION', 'VALIDATION_IN_PROGRESS', 'VALIDATION_COMPLETE', 'VALIDATION_FAILED', 'REPLACED')) NOT NULL,
    schedule_number         TEXT,
    previous_submission_id  UUID,
    is_nil_submission       BOOLEAN,
    number_of_claims        INTEGER,
    error_messages          TEXT,
    created_by_user_id      UUID NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    modified_by_user_id     UUID,
    modified_on             TIMESTAMPTZ
);
CREATE INDEX idx_submissions_bulk_submission_id ON submissions(bulk_submission_id);
CREATE INDEX idx_submissions_provider_office_id ON submissions(provider_office_id);
CREATE INDEX idx_submissions_provider_user_id ON submissions(provider_user_id);
