-- Add submitted_as_draft column to bulk_submission table
-- Indicates whether the bulk submission was initially saved as a draft or not. Does not mean
-- that the submission is still a draft, as the submission may have been submitted after being
-- saved as a draft.
ALTER TABLE claims.bulk_submission
    ADD COLUMN submitted_as_draft BOOLEAN DEFAULT FALSE;

-- Recreate with READY_FOR_FINAL_SUBMISSION added
ALTER TABLE claims.bulk_submission
    DROP CONSTRAINT chk_bulk_submission_status;

ALTER TABLE bulk_submission
    ADD CONSTRAINT chk_bulk_submission_status CHECK (status IN ('READY_FOR_PARSING', 'PARSING_COMPLETED', 'PARSING_FAILED', 'VALIDATION_FAILED', 'REPLACED', 'UNAUTHORISED', 'VALIDATION_SUCCEEDED', 'READY_FOR_FINAL_SUBMISSION'));