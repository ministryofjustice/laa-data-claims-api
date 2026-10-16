-- Add the statuses covering final validation: the API queues a submission once the provider has
-- approved it, and the event service marks it in progress when it picks the approval event up.

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
