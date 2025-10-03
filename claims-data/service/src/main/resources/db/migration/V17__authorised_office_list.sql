ALTER TABLE bulk_submission
ADD COLUMN authorised_offices TEXT;

-- Drop the old check constraint
ALTER TABLE bulk_submission
DROP CONSTRAINT chk_bulk_submission_status;

-- Recreate with UNAUTHORISED added
ALTER TABLE bulk_submission
ADD CONSTRAINT chk_bulk_submission_status CHECK (status IN ('READY_FOR_PARSING', 'PARSING_COMPLETED', 'PARSING_FAILED', 'VALIDATION_FAILED', 'REPLACED', 'UNAUTHORISED'));