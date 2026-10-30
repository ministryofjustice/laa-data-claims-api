-- Record when a provider approved a submission for final validation, and who approved it.
-- updated_on and updated_by_user_id cannot serve this purpose: the event service overwrites them
-- as final validation progresses, so the moment of approval would be lost within seconds.

ALTER TABLE submission
    ADD COLUMN IF NOT EXISTS approved_on         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by_user_id TEXT;
