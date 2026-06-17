-- Allow bulk_submission_id to be NULL on the submission table.
-- The existing FK constraint already enforces referential integrity when a value is present;
-- dropping NOT NULL means NULL (no associated bulk submission) is also permitted.

ALTER TABLE submission
    ALTER COLUMN bulk_submission_id DROP NOT NULL;

