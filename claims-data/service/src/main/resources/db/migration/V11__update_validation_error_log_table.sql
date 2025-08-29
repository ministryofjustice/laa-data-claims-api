ALTER TABLE validation_error_log
ALTER COLUMN claim_id DROP NOT NULL;

ALTER TABLE validation_error_log
DROP COLUMN IF EXISTS error_timestamp;