CREATE TABLE validation_error_log (
  id                    UUID PRIMARY KEY,
  submission_id         UUID NOT NULL REFERENCES submission(id),
  claim_id              UUID NOT NULL REFERENCES claim(id),
  error_code            TEXT NOT NULL,
  error_description     TEXT NOT NULL,
  error_timestamp       TIMESTAMPTZ NOT NULL,
  created_by_user_id    TEXT NOT NULL,
  created_on            TIMESTAMPTZ NOT NULL,
  updated_by_user_id    TEXT,
  updated_on            TIMESTAMPTZ
);
CREATE INDEX idx_validation_error_log_submission_id ON validation_error_log(submission_id);
CREATE INDEX idx_validation_error_log_claim_id ON validation_error_log(claim_id);
