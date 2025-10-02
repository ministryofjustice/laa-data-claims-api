-- V15__remove_mandatory_claim_fields.sql

ALTER TABLE claim
ALTER COLUMN unique_file_number DROP NOT NULL;

ALTER TABLE claim
ALTER COLUMN case_reference_number DROP NOT NULL;

ALTER TABLE claim
ALTER COLUMN schedule_reference DROP NOT NULL;

ALTER TABLE claim
ALTER COLUMN case_start_date DROP NOT NULL;

ALTER TABLE claim_case
ALTER COLUMN outcome_code DROP NOT NULL;
