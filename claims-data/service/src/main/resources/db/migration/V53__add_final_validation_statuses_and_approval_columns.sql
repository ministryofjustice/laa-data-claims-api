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

-- Record when a provider approved a submission for final validation, and who approved it.
-- updated_on and updated_by_user_id cannot serve this purpose: the event service overwrites them
-- as final validation progresses, so the moment of approval would be lost within seconds.

ALTER TABLE submission
    ADD COLUMN IF NOT EXISTS approved_on         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by_user_id TEXT;
