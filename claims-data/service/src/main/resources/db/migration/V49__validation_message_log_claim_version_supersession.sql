ALTER TABLE claims.validation_message_log
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN superseded_by_version bigint NOT NULL DEFAULT 0;

UPDATE claims.validation_message_log
   SET version = 0
 WHERE version IS NULL;

UPDATE claims.validation_message_log
   SET superseded_by_version = 0
 WHERE superseded_by_version IS NULL;

ALTER TABLE claims.validation_message_log
    DROP CONSTRAINT IF EXISTS fk_validation_message_log_claim_amendment;

DROP INDEX IF EXISTS ix_validation_message_log_claim_amendment_id;

ALTER TABLE claims.validation_message_log
    DROP COLUMN IF EXISTS claim_amendment_id;

CREATE INDEX IF NOT EXISTS ix_validation_message_log_claim_id_source_superseded_by_version
    ON claims.validation_message_log (claim_id, source, superseded_by_version);

CREATE INDEX IF NOT EXISTS ix_validation_message_log_submission_id_type_superseded_by_version
    ON claims.validation_message_log (submission_id, type, superseded_by_version);
