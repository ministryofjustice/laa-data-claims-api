DROP TABLE IF EXISTS claims.validation_error_log;

CREATE TABLE claims.validation_message_log (
   id uuid NOT NULL,
   submission_id uuid NOT NULL,
   claim_id uuid NULL,
   type text NOT NULL,
   source text NOT NULL,
   display_message text NOT NULL,
   technical_message text NULL,
   created_on timestamptz NOT NULL,

   CONSTRAINT pk_validation_message_log PRIMARY KEY (id),
   CONSTRAINT fk_validation_message_log_claim_id FOREIGN KEY (claim_id) REFERENCES claim(id),
   CONSTRAINT fk_validation_message_log_submission_id FOREIGN KEY (submission_id) REFERENCES submission(id)
);

CREATE INDEX ix_validation_message_log_claim_id ON claims.validation_message_log (claim_id);
CREATE INDEX ix_validation_message_log_submission_id ON claims.validation_message_log (submission_id);
CREATE INDEX ix_validation_message_log_type ON claims.validation_message_log (type);