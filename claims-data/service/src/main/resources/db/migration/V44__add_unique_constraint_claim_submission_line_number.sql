-- Enforce the business rule that a claim's line_number must be unique within a submission.
-- Duplicate (submission_id, line_number) combinations must never exist.
--
-- IMPORTANT (pre-deployment): this migration will fail if duplicate rows already exist.
-- Run the duplicate-detection query documented in
-- docs/release-notes/unique-claim-submission-line-number.md and resolve any duplicates
-- BEFORE deploying. Do not add automated data cleanup here.
ALTER TABLE claim
    ADD CONSTRAINT uq_claim_submission_line_number
        UNIQUE (submission_id, line_number);

