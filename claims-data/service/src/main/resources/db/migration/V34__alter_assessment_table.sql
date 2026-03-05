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

ALTER TABLE assessment
    ALTER COLUMN assessment_outcome DROP NOT NULL;