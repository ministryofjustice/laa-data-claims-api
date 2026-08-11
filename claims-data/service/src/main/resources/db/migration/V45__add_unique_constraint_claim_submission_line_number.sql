-- Enforce that a claim's line_number is unique within a submission, going forward.
--
-- This is a PARTIAL unique index rather than a plain unique constraint so that environments which
-- already contain historical duplicates (business rule: we never amend or delete historical data)
-- can adopt it without the index build failing. The cutoff is supplied by the Flyway placeholder
-- ${claim_line_number_uniqueness_cutoff}. Local/CI default this to the epoch (see application.yml) so
-- clean/fresh databases enforce uniqueness for ALL rows. Deployed environments have the Helm chart
-- set CLAIM_LINE_NUMBER_UNIQUENESS_CUTOFF to the deploy time, so all rows existing at deploy are
-- grandfathered and uniqueness is enforced only for claims created afterwards, without any manual
-- per-environment configuration (see docs/release-notes/unique-claim-submission-line-number.md).
--
-- CAVEAT (old-vs-new): a partial index only enforces uniqueness among the rows it covers, so a new
-- claim could in theory duplicate a grandfathered historical row sitting below the cutoff. This is
-- currently unreachable because claims are only ever added to newly-created submissions, never
-- appended to historical ones. The application-level pre-check in ClaimService.createClaim closes
-- this gap defensively (with a minimal residual race) should that rule ever change.
--
-- The index is named uq_claim_submission_line_number so that unique-violation errors report that
-- name, which DataClaimsExceptionHandler uses to map the violation to HTTP 409 as a backstop.
--
-- NOTE: for a very large table in an environment with existing data, consider building this index
-- with CREATE UNIQUE INDEX CONCURRENTLY in a non-transactional migration to avoid a write lock.
CREATE UNIQUE INDEX uq_claim_submission_line_number
    ON claim (submission_id, line_number)
    WHERE created_on > TIMESTAMPTZ '${claim_line_number_uniqueness_cutoff}';

