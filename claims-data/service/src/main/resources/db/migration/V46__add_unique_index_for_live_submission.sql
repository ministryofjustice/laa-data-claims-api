-- Enforce that only one "live" submission can exist for a given office, area of law and
-- submission period. A submission is considered live unless it has been superseded, i.e. its
-- status is neither VALIDATION_FAILED nor REPLACED, so a failed or replaced submission does not
-- block a fresh attempt for the same office/area-of-law/period combination.
--
-- This is a PARTIAL unique index rather than a plain unique constraint so that environments which
-- already contain historical duplicates (business rule: we never amend or delete historical data)
-- can adopt it without the index build failing. The cutoff is supplied by the Flyway placeholder
-- ${live_submission_uniqueness_cutoff}. Local/CI default this to the epoch (see application.yml) so
-- clean/fresh databases enforce uniqueness for ALL rows. Deployed environments have the Helm chart
-- set LIVE_SUBMISSION_UNIQUENESS_CUTOFF to the deploy time, so all rows existing at deploy are
-- grandfathered and uniqueness is enforced only for submissions created afterwards, without any
-- manual per-environment configuration.
--
-- The application already enforces the same uniqueness rule in SubmissionService.createSubmission,
-- so this index is a race-safe back-stop to catch anything the application misses. The index is
-- named uq_submission_live_office_aol_period so that unique-violation errors report that name, which
-- DataClaimsExceptionHandler uses to map the violation to HTTP 409 as a backstop.
CREATE UNIQUE INDEX uq_submission_live_office_aol_period
    ON submission (
        office_account_number,
        area_of_law,
        submission_period
    )
    WHERE status NOT IN ('VALIDATION_FAILED', 'REPLACED')
      AND created_on > TIMESTAMPTZ '${live_submission_uniqueness_cutoff}';
