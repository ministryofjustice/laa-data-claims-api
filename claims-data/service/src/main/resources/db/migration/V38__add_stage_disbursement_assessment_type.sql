-- Add STAGE_DISBURSEMENT_ASSESSMENT to allowed assessment_type values
ALTER TABLE assessment
    DROP CONSTRAINT IF EXISTS chk_assessment_type;

ALTER TABLE assessment
    ADD CONSTRAINT chk_assessment_type
        CHECK (
            assessment_type IN ('ESCAPE_CASE_ASSESSMENT', 'STAGE_DISBURSEMENT_ASSESSMENT', 'VOID')
            );
