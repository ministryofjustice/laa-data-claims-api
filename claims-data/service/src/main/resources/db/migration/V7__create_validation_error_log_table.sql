CREATE TABLE validation_error_log (
  id                    UUID NOT NULL,
  submission_id         UUID NOT NULL,
  claim_id              UUID NOT NULL,
  error_code            TEXT NOT NULL,
  error_description     TEXT NOT NULL,
  error_timestamp       TIMESTAMPTZ NOT NULL,
  created_by_user_id    TEXT NOT NULL,
  created_on            TIMESTAMPTZ NOT NULL,
  updated_by_user_id    TEXT,
  updated_on            TIMESTAMPTZ,

  CONSTRAINT pk_validation_error_log PRIMARY KEY (id),
  CONSTRAINT fk_validation_error_log_submission_id FOREIGN KEY (submission_id) REFERENCES submission(id),
  CONSTRAINT fk_validation_error_log_claim_id FOREIGN KEY (claim_id) REFERENCES claim(id)
);
CREATE INDEX ix_validation_error_log_submission_id ON validation_error_log(submission_id);
CREATE INDEX ix_validation_error_log_claim_id ON validation_error_log(claim_id);
