CREATE INDEX IF NOT EXISTS ix_submission_office_status_created
    ON claims.submission (office_account_number, status, created_on DESC);

CREATE INDEX IF NOT EXISTS ix_submission_office_period_area
    ON claims.submission (office_account_number, submission_period, area_of_law);

CREATE INDEX IF NOT EXISTS ix_claim_submission_status
    ON claims.claim (submission_id, status);

CREATE INDEX IF NOT EXISTS ix_claim_fee_code
    ON claims.claim (fee_code);

CREATE INDEX IF NOT EXISTS ix_claim_unique_file_number
    ON claims.claim (unique_file_number);

CREATE INDEX IF NOT EXISTS ix_client_ucn_claim
    ON claims.client (unique_client_number, claim_id);

CREATE INDEX IF NOT EXISTS ix_claim_case_unique_claim
    ON claims.claim_case (unique_case_id, claim_id);

CREATE INDEX IF NOT EXISTS ix_vml_submission_type_claim
    ON claims.validation_message_log (submission_id, claim_id, type);