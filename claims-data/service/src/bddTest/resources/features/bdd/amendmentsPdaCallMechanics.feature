@Regression
@amendments
@pda
@dstew-1773
Feature: PDA re-validation — call mechanics (cache, single-attempt timeout, dedup)

  # Jira: DSTEW-1773 (parent: DSTEW-1646 → DSTEW-1999)
  # Endpoints: POST /api/v1/bulk-submissions, GET /api/v1/bulk-submissions/{id}/summary
  #
  # Invoked only when DSTEW-1772 trigger sets pda_relevant=true.
  # Uses post-amendment officeCode + resolved effectiveDate as the key.
  # Single synchronous attempt, amendment-path per-attempt timeout, no retries.
  # Amendment-path config independent of the new-submission PDA config.
  #
  # Coverage review (2026-08-05) — scenarios DROPPED as covered end-to-end by
  # ClaimAmendmentPdaCallIntegrationTest:
  #   original @PDA_1 → notTriggeredNonPdaFieldChangeMakesNoOutboundCall
  #   original @PDA_2 → cacheHitSecondAmendmentWithSameKeyMakesNoOutboundCall
  #   original @PDA_3 → cacheMissPdaRelevantFieldChangeMakesSingleOutboundCall
  #   original @PDA_5 → timeoutSlowResponseMakesSingleAttemptWithNoRetry
  # Only the 4 gap scenarios remain.
  #
  # OUT OF SCOPE: PDA outcome mapping → amendmentsPdaOutcomeMapping.feature;
  #               Parent-flow integration → amendmentsPdaParentIntegration.feature.

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"

  @PDA_4
  Scenario: Successful PDA response inside the configured amendment-path timeout is returned as-is
    # Gap: integration suite has no success-under-budget scenario on the amendment route.
    Given no PDA cache entry exists for officeCode "OFC-004" and effectiveDate "2026-04-01"
    And the amendment-path PDA per-attempt timeout is configured to 5 seconds
    And the PDA service will respond successfully after 2 seconds
    And an amendment submission with the following claims
      | ucn             | ufn        | feeCode | office  | effectiveDate |
      | 14091962/T/PERS | 010725/123 | ASSA    | OFC-004 | 2026-04-01    |
    When I submit it and wait for the event service to complete amendment validation
    Then exactly 1 outbound PDA call was made
    And the amendment processing was not aborted by any Claims-API response-time limit
    And PDA monitoring records outcome "success"

  @PDA_6
  Scenario: Concurrent amendments for the same officeCode/effectiveDate deduplicate to a single PDA call
    # Gap: no multi-threaded submission test exists; cache-hit test proves sequential
    # same-key reuse but not in-flight dedup.
    Given no PDA cache entry exists for officeCode "OFC-006" and effectiveDate "2026-04-01"
    And the PDA service will respond successfully after 3 seconds
    When I submit the following amendment submissions concurrently
      | ucn             | ufn        | feeCode | office  | effectiveDate |
      | 14091962/T/PERS | 010725/123 | ASSA    | OFC-006 | 2026-04-01    |
      | 14091962/T/PERS | 010725/124 | ASSA    | OFC-006 | 2026-04-01    |
    And I wait for the event service to complete amendment validation for both
    Then exactly 1 outbound PDA call was made
    And both submissions received the same PDA outcome

  @PDA_7
  Scenario: Amendment-path PDA timeout is independent of the new-submission PDA timeout
    # Gap: integration test overrides the amendment-path timeout but never asserts
    # that the new-submission timeout is unaffected.
    Given the new-submission PDA per-attempt timeout is configured to 30 seconds
    And the amendment-path PDA per-attempt timeout is configured to 2 seconds
    And no PDA cache entry exists for officeCode "OFC-007" and effectiveDate "2026-04-01"
    And the PDA service will not respond before 10 seconds
    And an amendment submission with the following claims
      | ucn             | ufn        | feeCode | office  | effectiveDate |
      | 14091962/T/PERS | 010725/123 | ASSA    | OFC-007 | 2026-04-01    |
    When I submit it and wait for the event service to complete amendment validation
    Then the PDA outcome reported to downstream processing is "timeout"
    And the new-submission PDA per-attempt timeout remained 30 seconds

  @PDA_8
  Scenario: PDA request uses post-amendment officeCode and effectiveDate, not pre-amendment values
    # Gap: existing test proves the amended values ARE used but does not assert
    # pre-amendment values are NOT used.
    Given an original claim exists with officeCode "OFC-OLD" and effectiveDate "2025-04-01"
    And an amendment updates the claim to officeCode "OFC-NEW" and effectiveDate "2026-04-01"
    And no PDA cache entry exists for officeCode "OFC-NEW" and effectiveDate "2026-04-01"
    And the PDA service will respond successfully within the amendment-path timeout
    When I submit it and wait for the event service to complete amendment validation
    Then exactly 1 outbound PDA call was made
    And the outbound PDA request used officeCode "OFC-NEW" and effectiveDate "2026-04-01"
    And no outbound PDA request was made using officeCode "OFC-OLD" or effectiveDate "2025-04-01"

