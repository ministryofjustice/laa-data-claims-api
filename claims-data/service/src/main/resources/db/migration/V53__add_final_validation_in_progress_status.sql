-- Add the final-validation in-progress status used to claim a submission for provider approval.

ALTER TABLE submission
    DROP CONSTRAINT IF EXISTS chk_submission_status;

ALTER TABLE submission
    ADD CONSTRAINT chk_submission_status CHECK (status IN (
        'CREATED',
        'READY_FOR_VALIDATION',
        'VALIDATION_IN_PROGRESS',
        'FINAL_VALIDATION_IN_PROGRESS',
        'VALIDATION_SUCCEEDED',
        'VALIDATION_FAILED',
        'REPLACED',
        'VALIDATED_PENDING_APPROVAL'
    ));
