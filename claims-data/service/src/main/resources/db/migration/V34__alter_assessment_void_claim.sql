ALTER TABLE assessment
    ALTER COLUMN assessment_outcome DROP NOT NULL;

ALTER TABLE assessment
    ADD COLUMN assessment_type varchar(255);

ALTER TABLE assessment
    ADD CONSTRAINT chk_assessment_type
        CHECK (
            assessment_type IS NULL OR
            assessment_type IN ('ESCAPE_CASE_ASSESSMENT', 'VOID')
            );

ALTER TABLE assessment
    ADD COLUMN assessment_reason varchar(255);

ALTER TABLE claim
    DROP CONSTRAINT chk_claim_status;

ALTER TABLE claim
    ADD CONSTRAINT chk_claim_status
        CHECK (status IN ('READY_TO_PROCESS', 'VALID', 'INVALID', 'VOID'));