@Regression
@amendments
@pda
@dstew-1773
@dstew-1774
@dstew-1646
Feature: PDA re-validation in the claim amendment flow — call mechanics, outcome mapping & integration

  # Consolidates three PDA stories that together deliver Step 10 of the
  # amendment validation flow:
  #   * DSTEW-1773 → synchronous PDA call, cache, single-attempt timeout
  #   * DSTEW-1774 → outcome mapping (validation messages + terminal technical)
  #   * DSTEW-1646 → parent-level integration (ordering, atomicity)
  # Sibling: amendmentsClassifier.feature (DSTEW-1772) supplies pda_relevant.
  #
  # Endpoints exercised:
  #   POST  /api/v1/bulk-submissions              — submit a bulk submission containing an amendment
  #   GET   /api/v1/bulk-submissions/{id}         — read persisted bulk submission
  #   GET   /api/v1/bulk-submissions/{id}/summary — poll for terminal status
  #   PATCH /api/v1/bulk-submissions/{id}         — drive terminal status (local mode)
  #   GET   /api/v1/validation-messages           — read PDA-mapped validation errors
  #   GET   /api/v1/claims/{id}                   — read persisted claim state (no-save assertions)
  #
  # Behaviour under test:
  #   Call layer (DSTEW-1773)
  #     * Invoked only when the DSTEW-1772 trigger sets pda_relevant=true.
  #     * Uses post-amendment officeCode + resolved effectiveDate as the key.
  #     * Reuses existing positive/negative cache and in-flight dedup.
  #     * Single synchronous attempt, amendment-path per-attempt timeout, no retries.
  #     * Timeout outcome returned when the configured external-service timeout
  #       is reached; NO additional Claims-API hard response-time limit.
  #     * Amendment-path config is independent of the new-submission PDA config.
  #     * Emits cache-hit/miss, call-duration and outcome monitoring.
  #
  #   Outcome mapping (DSTEW-1774)
  #     * PDA "no matching schedule for Area of Law"    → INVALID_AREA_OF_LAW_FOR_PROVIDER
  #     * PDA "category of law not authorised"          → INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER
  #     * Collected messages sit in the shared Step 12 multi-message response (DSTEW-1770).
  #     * Technical failures (HTTP 5xx, connection failure, schema/parse error,
  #       configured external-service timeout) → terminal controlled failure
  #       TECHNICAL_ERROR_PROVIDER_DETAILS_API. Terminal wins over any earlier
  #       collected validation messages in the same attempt.
  #     * Every PDA failure outcome is no-save to orchestration.
  #     * Logs and monitoring carry safe correlation/outcome/timing only — never
  #       amendment payload or financial values.
  #
  #   Integration (DSTEW-1646)
  #     * Eligibility / stale-version rejections short-circuit BEFORE PDA is called.
  #     * Post-PDA persistence failure rolls back atomically.
  #
  # OUT OF SCOPE:
  #   * Trigger rule / pda_relevant classification    → amendmentsClassifier.feature
  #   * Amendment metadata validation & reference data → amendmentsMetadata.feature
  #
  # Outbound PDA calls are stubbed (WireMock). Cache state is manipulated via
  # test hooks. Log-content assertions require a capturing appender in the BDD
  # test config; monitoring assertions require access to a Micrometer meter
  # registry or an equivalent monitoring endpoint.
  #
  # ----------------------------------------------------------------------------
  # Coverage review (2026-08-05) — scenarios DROPPED because unit/integration
  # tests already prove the behaviour end-to-end. See memory.md rule
  # "Unit / integration test coverage review".
  #   Integration test file:
  #     claims-data/service/src/integrationTest/java/uk/gov/justice/laa/dstew/
  #       payments/claimsdata/controller/claim/amendments/
  #         ClaimAmendmentPdaCallIntegrationTest.java
  #
  #   * PDA_1  covered by  notTriggeredNonPdaFieldChangeMakesNoOutboundCall()
  #   * PDA_2  covered by  cacheHitSecondAmendmentWithSameKeyMakesNoOutboundCall()
  #   * PDA_3  covered by  cacheMissPdaRelevantFieldChangeMakesSingleOutboundCall()
  #            (parameterised across 5 PDA-impacting fields)
  #   * PDA_5  covered by  timeoutSlowResponseMakesSingleAttemptWithNoRetry()
  #            (single attempt via resilience4j.pdaRetry.maxAttempts=1,
  #             elapsedMs asserted against timeout boundary)
  # ----------------------------------------------------------------------------

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"

  # ============================================================================
  # DSTEW-1773 — Call mechanics (only the coverage gaps remain)
  # ============================================================================

  @PDA_4
  Scenario: Successful PDA response inside the configured amendment-path timeout is returned as-is
    # Gap: integration suite has no success-under-budget scenario; stubs exist
    # (MockServerIntegrationTest:342-343, 355-359) but are not exercised for
    # the happy path on the amendment route.
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
    # Gap: no multi-threaded submission test exists in the integration suite;
    # cache-hit test proves sequential same-key reuse but not in-flight dedup.
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
    # Gap: integration test overrides the amendment-path timeout
    # (ClaimAmendmentPdaCallIntegrationTest:80-81) but never asserts that the
    # new-submission timeout is unaffected. This scenario closes that gap.
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
    # Gap: `cacheMissPdaRelevantFieldChangeMakesSingleOutboundCall` proves the
    # amended values ARE used (parameterised across PdaImpactingField enum,
    # ClaimAmendmentPdaCallIntegrationTest:211-250) but does not assert the
    # pre-amendment values are NOT used. This scenario adds the exclusion
    # assertion.
    Given an original claim exists with officeCode "OFC-OLD" and effectiveDate "2025-04-01"
    And an amendment updates the claim to officeCode "OFC-NEW" and effectiveDate "2026-04-01"
    And no PDA cache entry exists for officeCode "OFC-NEW" and effectiveDate "2026-04-01"
    And the PDA service will respond successfully within the amendment-path timeout
    When I submit it and wait for the event service to complete amendment validation
    Then exactly 1 outbound PDA call was made
    And the outbound PDA request used officeCode "OFC-NEW" and effectiveDate "2026-04-01"
    And no outbound PDA request was made using officeCode "OFC-OLD" or effectiveDate "2025-04-01"

  # ============================================================================
  # DSTEW-1774 — Outcome mapping
  # (Coverage note: stub infrastructure exists in MockServerIntegrationTest
  # lines 367-414 — HTTP 5xx, connection drop, malformed body, delayed
  # response — but no integration test currently invokes these stubs and
  # asserts the returned error codes. All DS1774_* scenarios remain in scope.)
  # ============================================================================

  @DS1774_1
  @smoke
  Scenario: PDA returns no schedule matching post-amendment Area of Law → INVALID_AREA_OF_LAW_FOR_PROVIDER collected
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-A01" and effectiveDate "2025-04-01"
    And an amendment updates the claim to a fee code whose Area of Law is not on any PDA schedule for the provider
    And the PDA service will return a schedule set with no matching Area of Law
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                       |
      | INVALID_AREA_OF_LAW_FOR_PROVIDER |
    And the validation message is returned in the shared Step 12 multi-message response
    And no amendment state was committed

  @DS1774_2
  Scenario: PDA returns no schedule authorising post-amendment Category of Law → INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER collected
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-A02" and effectiveDate "2025-04-01"
    And an amendment updates the claim to a fee code whose Category of Law is not authorised by any PDA schedule for the provider
    And the PDA service will return a schedule set with the Area of Law present but the Category of Law unauthorised
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                                          |
      | INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER |
    And the validation message is returned in the shared Step 12 multi-message response
    And no amendment state was committed

  @DS1774_3
  Scenario: PDA validation messages are aggregated with other collected validation messages in the Step 12 response
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-A03" and effectiveDate "2025-04-01"
    And an amendment updates the claim to a fee code whose Area of Law is not on any PDA schedule for the provider
    And the amendment also fails an unrelated field-level validation with code "INVALID_FIELD_VALUE"
    And the PDA service will return a schedule set with no matching Area of Law
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors in any order
      | Error Code                       |
      | INVALID_AREA_OF_LAW_FOR_PROVIDER |
      | INVALID_FIELD_VALUE              |
    And no amendment state was committed

  @DS1774_4
  Scenario Outline: PDA <failureKind> maps to TECHNICAL_ERROR_PROVIDER_DETAILS_API terminal controlled failure
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-A04" and effectiveDate "2025-04-01"
    And an amendment updates the claim to feeCode "IMCA" and effectiveDate "2026-04-01"
    And the amendment-path PDA per-attempt timeout is configured to 2 seconds
    And the PDA service will <pdaBehaviour>
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the endpoint responds with a controlled terminal failure "TECHNICAL_ERROR_PROVIDER_DETAILS_API"
    And no amendment validation messages are returned alongside the terminal failure
    And no amendment state was committed

    Examples:
      | failureKind              | pdaBehaviour                       |
      | HTTP 5xx                 | respond with HTTP 503              |
      | connection failure       | reject the connection              |
      | schema/parse error       | respond with a malformed JSON body |
      | external-service timeout | not respond before 10 seconds      |

  @DS1774_5
  Scenario: A late PDA technical failure supersedes validation messages already collected in the same attempt
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-A05" and effectiveDate "2025-04-01"
    And an amendment updates the claim to feeCode "IMCA" and effectiveDate "2026-04-01"
    And an earlier validation step has already collected a validation message with code "INVALID_FIELD_VALUE"
    And the PDA service will respond with HTTP 500
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the endpoint responds with a controlled terminal failure "TECHNICAL_ERROR_PROVIDER_DETAILS_API"
    And the response does not contain a validation message with code "INVALID_FIELD_VALUE"
    And no amendment state was committed

  @DS1774_6
  Scenario: PDA technical-failure logs carry safe support context and exclude amendment payload values
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-A06" and effectiveDate "2025-04-01"
    And an amendment updates the claim to feeCode "IMCA" and effectiveDate "2026-04-01"
    And the PDA service will respond with HTTP 500
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the technical failure log entry contains the correlation identifier
    And the technical failure log entry contains the PDA outcome "technical_failure"
    And the technical failure log entry does not contain any amendment payload field values
    And the technical failure log entry does not contain any financial values

  @DS1774_7
  Scenario Outline: PDA <failureCategory> outcome is handed to orchestration as a no-save outcome
    Given an original claim exists with feeCode "ASSA" and officeCode "<office>" and effectiveDate "2025-04-01"
    And an amendment updates the claim to feeCode "IMCA" and effectiveDate "2026-04-01"
    And the PDA service will <pdaBehaviour>
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the PDA outcome handed to orchestration is marked as no-save
    And no amendment record, diff, calculated-fee child row, event or claim-state update was committed
    And the claim persisted state matches the pre-amendment state

    Examples:
      | failureCategory    | office   | pdaBehaviour                                       |
      | validation failure | OFC-A07 | return a schedule set with no matching Area of Law |
      | technical failure  | OFC-A08 | respond with HTTP 500                              |
      | external timeout   | OFC-A09 | not respond before 10 seconds                      |

  @DS1774_8
  Scenario Outline: PDA outcome monitoring emits <signal> without leaking payload values
    Given an original claim exists with feeCode "ASSA" and officeCode "<office>" and effectiveDate "2025-04-01"
    And an amendment updates the claim to feeCode "IMCA" and effectiveDate "2026-04-01"
    And the PDA service will <pdaBehaviour>
    When I submit the amendment and wait for the event service to complete amendment validation
    Then PDA monitoring records outcome "<expectedOutcome>" with a non-zero call duration
    And PDA monitoring does not contain any amendment payload field values

    Examples:
      | signal             | office  | pdaBehaviour                                       | expectedOutcome    |
      | validation failure | OFC-A81 | return a schedule set with no matching Area of Law | validation_failure |
      | technical failure  | OFC-A82 | respond with HTTP 500                              | technical_failure  |
      | external timeout   | OFC-A83 | not respond before 10 seconds                      | timeout            |

  # ============================================================================
  # DSTEW-1646 — Parent-level integration (only scenarios NOT already proved
  # by DSTEW-1773 / DSTEW-1774 above)
  # ============================================================================

  @DS1646_1
  Scenario Outline: Early <earlyRejection> rejection short-circuits before PDA is called
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-500" and effectiveDate "2025-04-01"
    And an amendment that will fail the "<earlyRejection>" check
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected
    And no outbound PDA call was made
    And the claim persisted state matches the pre-amendment state

    Examples:
      | earlyRejection      |
      | eligibility gate    |
      | stale version check |

  @DS1646_2
  Scenario: Post-PDA persistence failure rolls back the amendment atomically
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-800" and effectiveDate "2025-04-01"
    And an amendment updates the claim to feeCode "IMCA" and effectiveDate "2026-04-01"
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the amendment persistence step will fail after PDA has returned success
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the endpoint responds with a controlled terminal failure
    And the claim persisted state matches the pre-amendment state
    And no partial amendment fields are visible on subsequent reads




