ALTER TABLE submission
    ADD COLUMN provider_user_id TEXT NOT NULL;

UPDATE submission s
SET provider_user_id = (
    SELECT bs.created_by_user_id
    FROM bulk_submission bs
    WHERE bs.id = s.bulk_submission_id
);