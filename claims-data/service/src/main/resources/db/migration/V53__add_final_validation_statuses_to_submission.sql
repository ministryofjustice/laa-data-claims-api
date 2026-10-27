-- Allow the submission lifecycle statuses introduced by the shared final-validation model.

ALTER TABLE submission
    DROP CONSTRAINT IF EXISTS chk_submission_status;

ALTER TABLE submission
    ADD CONSTRAINT chk_submission_status CHECK (status IN (
        'CREATED',
        'READY_FOR_VALIDATION',
        'VALIDATION_IN_PROGRESS',
        'READY_FOR_FINAL_VALIDATION',
        'FINAL_VALIDATION_IN_PROGRESS',
        'VALIDATION_SUCCEEDED',
        'VALIDATION_FAILED',
        'REPLACED',
        'VALIDATED_PENDING_APPROVAL'
    ));
