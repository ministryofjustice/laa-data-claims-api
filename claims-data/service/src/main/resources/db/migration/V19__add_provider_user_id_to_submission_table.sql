ALTER TABLE submission
    ADD COLUMN provider_user_id TEXT;

UPDATE submission s
SET provider_user_id = (
    SELECT bs.created_by_user_id
    FROM bulk_submission bs
    WHERE bs.id = s.bulk_submission_id
);

ALTER TABLE submission ALTER COLUMN provider_user_id SET NOT NULL;