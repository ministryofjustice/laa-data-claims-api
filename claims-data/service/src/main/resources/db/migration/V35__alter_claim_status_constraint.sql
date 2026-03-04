ALTER TABLE claim
DROP CONSTRAINT chk_claim_status;

ALTER TABLE claim
    ADD CONSTRAINT chk_claim_status
        CHECK (status IN ('READY_TO_PROCESS', 'VALID', 'INVALID', 'VOID'));

ALTER TABLE assessment
    ALTER COLUMN assessment_outcome DROP NOT NULL;
