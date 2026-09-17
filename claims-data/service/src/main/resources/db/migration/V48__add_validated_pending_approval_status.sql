-- Add the new VALIDATED_PENDING_APPROVAL lifecycle status to the submission, claim and bulk_submission
-- status CHECK constraints. A submission (and its claims / bulk submission) moves to
-- VALIDATED_PENDING_APPROVAL when it passes INITIAL validation and is held for provider review before
-- Final Submit. The claims are not yet accepted (VALID) at this point.

ALTER TABLE submission
    DROP CONSTRAINT chk_submission_status;
ALTER TABLE submission
    ADD CONSTRAINT chk_submission_status CHECK (status IN ('CREATED', 'READY_FOR_VALIDATION', 'VALIDATION_IN_PROGRESS', 'VALIDATION_SUCCEEDED', 'VALIDATION_FAILED', 'REPLACED', 'VALIDATED_PENDING_APPROVAL'));

ALTER TABLE claim
    DROP CONSTRAINT chk_claim_status;
ALTER TABLE claim
    ADD CONSTRAINT chk_claim_status CHECK (status IN ('READY_TO_PROCESS', 'VALID', 'INVALID', 'VOID', 'VALIDATED_PENDING_APPROVAL'));

ALTER TABLE bulk_submission
    DROP CONSTRAINT chk_bulk_submission_status;
ALTER TABLE bulk_submission
    ADD CONSTRAINT chk_bulk_submission_status CHECK (status IN ('READY_FOR_PARSING', 'PARSING_COMPLETED', 'PARSING_FAILED', 'VALIDATION_FAILED', 'REPLACED', 'UNAUTHORISED', 'VALIDATION_SUCCEEDED', 'VALIDATED_PENDING_APPROVAL'));
