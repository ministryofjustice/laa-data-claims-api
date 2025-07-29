CREATE TABLE validation_error_logs (
  id                    UUID PRIMARY KEY,
  submission_id         UUID NOT NULL REFERENCES submissions(id),
  claim_id              UUID NOT NULL REFERENCES claims(id),
  error_code            TEXT NOT NULL,
  error_description     TEXT NOT NULL,
  error_timestamp       TIMESTAMPTZ NOT NULL,
  created_by_user_id    UUID NOT NULL,
  created_on            TIMESTAMPTZ NOT NULL,
  modified_by_user_id   UUID,
  modified_on           TIMESTAMPTZ
);
CREATE INDEX idx_validation_error_logs_submission_id ON validation_error_logs(submission_id);
CREATE INDEX idx_validation_error_logs_claim_id ON validation_error_logs(claim_id);
