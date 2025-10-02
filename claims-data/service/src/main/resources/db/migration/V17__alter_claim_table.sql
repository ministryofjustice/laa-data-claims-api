ALTER TABLE claim
ALTER COLUMN case_reference_number DROP NOT NULL,
ALTER COLUMN schedule_reference DROP NOT NULL,
ALTER COLUMN case_start_date DROP NOT NULL,
ALTER COLUMN case_concluded_date DROP NOT NULL;
