-- Enforce that only one "live" submission can exist for a given office, area of law and
-- submission period. A submission is considered live unless it has been superseded, i.e. its
-- status is neither VALIDATION_FAILED nor REPLACED, so a failed or replaced submission does not
-- block a fresh attempt for the same office/area-of-law/period combination.
--
-- The created_on cut-over guard scopes the constraint to submissions created after the fixed
-- go-live instant below. This is a back-stop only: the application already enforces the same
-- uniqueness rule, so the index exists to catch anything the application misses.
CREATE UNIQUE INDEX uq_submission_live_office_aol_period
    ON submission (
        office_account_number,
        area_of_law,
        submission_period
    )
    WHERE status NOT IN ('VALIDATION_FAILED', 'REPLACED')
      AND created_on > TIMESTAMPTZ '2026-09-01 00:00:00+00';
