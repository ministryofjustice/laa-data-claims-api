ALTER TABLE submission
    RENAME COLUMN schedule_number TO crime_schedule_number;

ALTER TABLE submission
    ADD COLUMN civil_submission_reference TEXT,
    ADD COLUMN mediation_submission_reference TEXT;

ALTER TABLE matter_start
    ADD COLUMN mediation_type TEXT;

ALTER TABLE claim
    ALTER COLUMN case_reference_number DROP NOT NULL,
    ALTER COLUMN schedule_reference DROP NOT NULL,
    ALTER COLUMN case_start_date DROP NOT NULL,
    ALTER COLUMN case_concluded_date DROP NOT NULL;
