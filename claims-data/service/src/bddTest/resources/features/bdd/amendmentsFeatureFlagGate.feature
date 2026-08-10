@Regression
@amendments
@dstew-1905
Feature: Amendments feature flag gate (DSTEW-1905)

  # ============================================================================
  # DSTEW-1905 — application.yml feature-flag gate on the whole amendments flow
  # ============================================================================

  @smoke @AFF_1
  Scenario: Amendments enabled — a claim amendment is accepted
  Given the amendments feature flag is enabled
  And an existing claim ready to be amended
  And an amendment payload with the following metadata
  | requestedByCode | amendmentReasonCode | submittingUserId                     |
  | PROVIDER        | PROVIDER_ERROR      | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
  When I submit the amendment
  Then the amendment is accepted

  @AFF_2
  Scenario: Amendments disabled — a claim amendment is rejected with a fatal error
  Given the amendments feature flag is disabled
  And an existing claim ready to be amended
  And an amendment payload with the following metadata
  | requestedByCode | amendmentReasonCode | submittingUserId                     |
  | PROVIDER        | PROVIDER_ERROR      | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
  When I submit the amendment
  Then the amendment is rejected with the following errors
  | Error Code                          | Error Message                        |
  | INVALID_AMENDMENTS_FEATURE_DISABLED | Amendments are not currently enabled.|
  And the amendment response status is 503

  @AFF_3
  Scenario: Amendments flag not configured — defaults to disabled and rejects the amendment
  Given the amendments feature flag is not configured
  And an existing claim ready to be amended
  And an amendment payload with the following metadata
  | requestedByCode | amendmentReasonCode | submittingUserId                     |
  | PROVIDER        | PROVIDER_ERROR      | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
  When I submit the amendment
  Then the amendment is rejected with the following errors
  | Error Code                          | Error Message                        |
  | INVALID_AMENDMENTS_FEATURE_DISABLED | Amendments are not currently enabled.|
  And the amendment response status is 503

  @AFF_4
  Scenario: Amendments disabled — a non-amendment Mediation submission is still accepted
  Given the amendments feature flag is disabled
  And a Mediation "xml" submission with the following claims
  | ucn             | ufn        | feeCode | office |
  | 07081996/S/FEEA | 080625/123 | ASSA    | 0P322F |
  When I submit it and wait for the event service to complete the duplicate checks
  Then the Mediation submission is accepted

  @AFF_5
  Scenario: Amendments enabled — existing amendment validations still apply (feature-flag error is NOT surfaced)
  Given the amendments feature flag is enabled
  And an existing claim ready to be amended
  And an amendment payload that is missing required metadata
  When I submit the amendment
  Then the amendment is rejected
  And no amendment error code equals "INVALID_AMENDMENTS_FEATURE_DISABLED"
  And no amendment error message contains "laa.claims.api.amendments.enabled"