--This migration replaces the same version that gets run in the main profile.
--This has been done because in the preview deployments we create a new database and run all
--migrations there. The real Version 15 migration tries to create a user and fails because
--a user is a global entity (applies to all databases) and it already exists in the instance.

CREATE PUBLICATION claims_reporting_service_pub
    FOR TABLE claims.submission,
              claims.claim,
              claims.client,
              claims.claim_case,
              claims.claim_summary_fee,
              claims.calculated_fee_detail;
