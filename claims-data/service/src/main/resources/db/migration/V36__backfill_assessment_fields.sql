-- Backfill missing assessment_type values only
UPDATE assessment
SET
    assessment_type = 'ESCAPE_CASE_ASSESSMENT'
WHERE assessment_type IS NULL;

-- Backfill missing or empty assessment_reason values
UPDATE assessment
SET
    assessment_reason = 'Escape Fee Case Assessment'
WHERE assessment_reason IS NULL
   OR length(trim(assessment_reason)) = 0;

-- Make columns NOT NULL
ALTER TABLE assessment
    ALTER COLUMN assessment_type SET NOT NULL,
    ALTER COLUMN assessment_reason SET NOT NULL;
