ALTER TABLE submission
    RENAME COLUMN schedule_number TO crime_schedule_number;

ALTER TABLE submission
    ADD COLUMN civil_submission_reference TEXT,
    ADD COLUMN mediation_submission_reference TEXT;

ALTER TABLE matter_start
    ADD COLUMN mediation_type TEXT;
