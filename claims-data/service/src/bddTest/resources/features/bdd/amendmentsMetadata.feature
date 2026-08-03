@Regression
@amendments
@metadata
@dstew-1905
@dstew-1765
@dstew-1594
Feature: Amendment metadata — feature flag, submit-time validation & reference-data lookup

  # Consolidates three stories covering the amendment-metadata surface:
  #   * DSTEW-1905 → application.yml feature-flag gate on the whole amendments flow
  #   * DSTEW-1765 → Requested By / Amendment Reason / Entra UUID validation at submit
  #   * DSTEW-1594 → Governed reference data + GET lookup that DSTEW-1765 validates
  #                   against, and that AaBC caches for dropdown rendering
  #
  # Endpoints exercised:
  #   PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #                                                 — submit a claim amendment
  #   POST  /api/v1/bulk-submissions                — non-amendment ingestion path (used
  #                                                   only by the @AFF_4 negative scenario to
  #                                                   prove the amendments flag does not
  #                                                   affect standard submissions)
  #   GET   /api/v1/reference/amendment-metadata    — active Requested By values with reasons
  #
  # Behaviour under test:
  #   Feature flag (DSTEW-1905)
  #     * `laa.claims.api.amendments.enabled` read from application.yml (bound to
  #       ClaimsApiProperties.Amendments) and evaluated per-request.
  #         true              → amendment PATCH proceeds through validation
  #         false             → fatal 503 rejection carrying the error code
  #                             INVALID_AMENDMENTS_FEATURE_DISABLED and the message
  #                             "Amendments are not currently enabled."
  #         property missing  → defaults to false → same fatal 503 rejection
  #     * Additive: non-amendment bulk submissions unaffected when flag is off.
  #     * Additive: when flag is on, existing amendment validations still apply.
  #
  #   Submit-time metadata validation (DSTEW-1765)
  #     * Requested By and Amendment Reason are mandatory and must be stable codes.
  #     * Requested By must be active in Reference Data.
  #     * Amendment Reason must be active AND valid for the submitted Requested By.
  #     * Entra user id is validated only as a UUID structurally; Claims API does
  #       NOT check the user exists, nor perform display-name lookups.
  #     * Metadata failures are collected and returned together in the amendment
  #       ProblemDetail response.
  #     * Reference-data source unavailable → controlled terminal failure
  #       TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA; no save.
  #
  #   Reference-data lookup (DSTEW-1594)
  #     * Governed reference data for Requested By + party-scoped Amendment Reason.
  #     * Lookup returns ACTIVE values only, in configured display_order.
  #     * Inactive values disappear from the lookup without hard-delete.
  #     * Display-label edits do NOT change the underlying code (historical
  #       stability for persisted amendment records).
  #     * Values can be added / updated WITHOUT a service redeploy.
  #     * Reference row ids are UUIDv7.
  #     * Create/update audit columns are populated by seed/load and updates.
  #
  # OUT OF SCOPE:
  #   * PDA re-validation trigger / call / outcome mapping → amendmentsPda.feature
  #   * FSP pricing rule source                            → amendmentsClassifier.feature
  #
  # NOTE: DSTEW-1765 was originally written against placeholder codes
  # (RB_PROVIDER / AR_FEE_CORR / ...). DSTEW-1594 uses the BC-574 real names
  # (PROVIDER / PROVIDER_ERROR / ...). Both fixtures appear below in their
  # respective sections; step glue is expected to seed whichever set the
  # scenario references. Follow-up: reconcile onto BC-574 names once dev picks
  # up the reference-data ticket.
  #
  # NOTE (2026-07-31): the amendment path is the synchronous claim PATCH endpoint —
  # the event service is NOT involved. Scenarios below therefore use the domain
  # verbs "I submit the amendment" and "the amendment is accepted|rejected"
  # rather than the bulk-submission "wait for the event service" vocabulary.
  # The feature-flag property is `laa.claims.api.amendments.enabled` (bound to
  # ClaimsApiProperties.Amendments.enabled), NOT `feature.amendments.enabled`.

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

  # ============================================================================
  # DSTEW-1765 — Submit-time metadata validation
  #
  # Fixture used below (via DSTEW-1594 stub — placeholder-code set):
  #   Requested By: RB_PROVIDER (active), RB_CASEWORKER (active), RB_LEGACY (inactive)
  #   Amendment Reason: AR_FEE_CORR (active, valid for RB_PROVIDER),
  #                     AR_CATEGORY_FIX (active, valid for RB_CASEWORKER),
  #                     AR_RETIRED (inactive, was valid for RB_PROVIDER)
  # ============================================================================

  @smoke @DS1765_1
  Scenario: Valid metadata does not raise any metadata validation error
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment
    Then no metadata validation error is raised
    And the submitted metadata values are available for persistence

  @DS1765_2
  Scenario Outline: Requested By <case> is rejected with <expectedCode>
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode   | amendmentReasonCode | submittingUserId                     |
      | <requestedByCode> | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment
    Then the amendment is rejected with the following errors
      | Error Code     |
      | <expectedCode> |
    And no amendment state was committed

    Examples:
      | case          | requestedByCode  | expectedCode                    |
      | missing       |                  | INVALID_REQUESTED_BY_MISSING    |
      | unknown       | RB_NOT_IN_LOOKUP | INVALID_REQUESTED_BY_UNKNOWN    |
      | inactive      | RB_LEGACY        | INVALID_REQUESTED_BY_INACTIVE   |
      | display label | Provider         | INVALID_REQUESTED_BY_NOT_A_CODE |

  @DS1765_3
  Scenario Outline: Amendment Reason <case> is rejected with <expectedCode>
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode   | submittingUserId                     |
      | RB_PROVIDER     | <amendmentReasonCode> | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment
    Then the amendment is rejected with the following errors
      | Error Code     |
      | <expectedCode> |
    And no amendment state was committed

    Examples:
      | case          | amendmentReasonCode | expectedCode                        |
      | missing       |                     | INVALID_AMENDMENT_REASON_MISSING    |
      | unknown       | AR_NOT_IN_LOOKUP    | INVALID_AMENDMENT_REASON_UNKNOWN    |
      | inactive      | AR_RETIRED          | INVALID_AMENDMENT_REASON_INACTIVE   |
      | display label | Fee correction      | INVALID_AMENDMENT_REASON_NOT_A_CODE |

  @DS1765_4
  Scenario: Amendment Reason valid for one Requested By is rejected when paired with another Requested By
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_CATEGORY_FIX     | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment
    Then the amendment is rejected with the following errors
      | Error Code                                |
      | INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY |
    And no amendment state was committed

  @DS1765_5
  Scenario: Amendment Reason is accepted when paired with its valid Requested By
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_CASEWORKER   | AR_CATEGORY_FIX     | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment
    Then no metadata validation error is raised

  @DS1765_6
  Scenario Outline: Submitting user id "<userId>" is <result>
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId |
      | RB_PROVIDER     | AR_FEE_CORR         | <userId>         |
    When I submit the amendment
    Then <expectedAssertion>
    And no existence check against the identity provider was performed

    Examples:
      | userId                               | result               | expectedAssertion                                                                              |
      | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 | accepted             | no metadata validation error is raised                                                         |
      | not-a-uuid                           | rejected as non-UUID | the amendment is rejected with a validation message with code "INVALID_USER_IDENTIFIER_FORMAT" |
      | 8f14e45f-ceea-467a-b3c0              | rejected as non-UUID | the amendment is rejected with a validation message with code "INVALID_USER_IDENTIFIER_FORMAT" |
      |                                      | rejected as non-UUID | the amendment is rejected with a validation message with code "INVALID_USER_IDENTIFIER_FORMAT" |

  @DS1765_7
  Scenario: Multiple metadata errors are surfaced together in one response
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId |
      |                 |                     | not-a-uuid       |
    When I submit the amendment
    Then the amendment is rejected with the following errors in any order
      | Error Code                       |
      | INVALID_REQUESTED_BY_MISSING     |
      | INVALID_AMENDMENT_REASON_MISSING |
      | INVALID_USER_IDENTIFIER_FORMAT   |
    And each error is returned in the same amendment ProblemDetail response
    And no amendment state was committed

  @DS1765_8
  Scenario: Metadata validation does not perform display-name or current-user lookups
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment
    Then no display-name lookup was performed against reference data
    And no existence check against the identity provider was performed

  @DS1765_9
  Scenario: Reference data unavailable returns a controlled technical failure and no amendment is saved
    Given the amendments feature flag is enabled
    And an existing claim ready to be amended
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    And the amendment metadata reference-data source is unavailable
    When I submit the amendment
    Then the amendment endpoint responds with a controlled terminal failure "TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA"
    And no amendment state was committed

  # ============================================================================
  # DSTEW-1594 — Reference-data lookup + governance
  #
  # BC-574 seed fixture assumed below:
  #   PROVIDER              (10)  Provider
  #     PROVIDER_ERROR                     (10) Provider Error
  #     CASE_REOPENED_REBILLED             (20) Case re-opened and being billed again later
  #     RECOVERY_FROM_CLIENT_OR_OTHER_SIDE (30) Money recovered from client and/or other side (inc. stat charge)
  #   CONTRACT_MANAGEMENT   (20)  Contract Management
  #     INCORRECT_MEANS_ASSESSMENT (10) Incorrect Means Assessment
  #     OTHER                      (20) Other
  #   ASSURANCE             (30)  Assurance
  #     INCORRECT_MEANS_ASSESSMENT (10) Incorrect Means Assessment
  #     OTHER                      (20) Other
  # ============================================================================

  @smoke @DS1594_1
  Scenario: Lookup returns active Requested By values in display order
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When I request the amendment metadata reference lookup
    Then the lookup response lists the following Requested By values in order
      | code                | display_label       | display_order |
      | PROVIDER            | Provider            | 10            |
      | CONTRACT_MANAGEMENT | Contract Management | 20            |
      | ASSURANCE           | Assurance           | 30            |

  @DS1594_2
  Scenario: Each Requested By value carries only its own reasons in display order
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When I request the amendment metadata reference lookup
    Then the Requested By value "PROVIDER" carries the following reasons in order
      | code                               | display_label                                                    | display_order |
      | PROVIDER_ERROR                     | Provider Error                                                   | 10            |
      | CASE_REOPENED_REBILLED             | Case re-opened and being billed again later                      | 20            |
      | RECOVERY_FROM_CLIENT_OR_OTHER_SIDE | Money recovered from client and/or other side (inc. stat charge) | 30            |
    And the Requested By value "CONTRACT_MANAGEMENT" carries the following reasons in order
      | code                       | display_label              | display_order |
      | INCORRECT_MEANS_ASSESSMENT | Incorrect Means Assessment | 10            |
      | OTHER                      | Other                      | 20            |
    And the Requested By value "ASSURANCE" carries the following reasons in order
      | code                       | display_label              | display_order |
      | INCORRECT_MEANS_ASSESSMENT | Incorrect Means Assessment | 10            |
      | OTHER                      | Other                      | 20            |
    And the reason "PROVIDER_ERROR" is not listed under Requested By "ASSURANCE"
    And the reason "PROVIDER_ERROR" is not listed under Requested By "CONTRACT_MANAGEMENT"

  @DS1594_3
  Scenario: Inactive Requested By values are excluded from the lookup
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And the Requested By value "ASSURANCE" is marked inactive
    When I request the amendment metadata reference lookup
    Then the lookup response does not contain the Requested By value "ASSURANCE"
    And the lookup response does not contain any reasons scoped to Requested By "ASSURANCE"
    And the lookup response still contains the Requested By value "PROVIDER"
    And the lookup response still contains the Requested By value "CONTRACT_MANAGEMENT"

  @DS1594_4
  Scenario: Inactive Amendment Reason values are excluded from their Requested By
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And the Amendment Reason "OTHER" under Requested By "CONTRACT_MANAGEMENT" is marked inactive
    When I request the amendment metadata reference lookup
    Then the Requested By value "CONTRACT_MANAGEMENT" carries the following reasons in order
      | code                       | display_label              | display_order |
      | INCORRECT_MEANS_ASSESSMENT | Incorrect Means Assessment | 10            |
    And the Requested By value "ASSURANCE" still contains the reason "OTHER"

  @DS1594_5
  Scenario: Add a new Requested By value without redeploying the service
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And a new active Requested By value with code "AUDITOR", label "Auditor" and display_order 40 is loaded without redeploying the service
    When I request the amendment metadata reference lookup
    Then the lookup response contains the Requested By value "AUDITOR" with display label "Auditor" at display_order 40
    And the Requested By value "AUDITOR" carries no reasons

  @DS1594_6
  Scenario: Add a new Amendment Reason under an existing Requested By without redeploying
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And a new active Amendment Reason with code "OTHER" under Requested By "PROVIDER" with label "Other" and display_order 40 is loaded without redeploying the service
    When I request the amendment metadata reference lookup
    Then the Requested By value "PROVIDER" carries the following reasons in order
      | code                               | display_label                                                    | display_order |
      | PROVIDER_ERROR                     | Provider Error                                                   | 10            |
      | CASE_REOPENED_REBILLED             | Case re-opened and being billed again later                      | 20            |
      | RECOVERY_FROM_CLIENT_OR_OTHER_SIDE | Money recovered from client and/or other side (inc. stat charge) | 30            |
      | OTHER                              | Other                                                            | 40            |

  @DS1594_7
  Scenario: Editing a display label does not change the underlying code
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And the display label for Requested By code "PROVIDER" is updated to "Provider (Legal Aid)"
    When I request the amendment metadata reference lookup
    Then the Requested By value with code "PROVIDER" has display label "Provider (Legal Aid)"
    And the Requested By code "PROVIDER" is unchanged
    And every Amendment Reason previously scoped to Requested By "PROVIDER" is still scoped to "PROVIDER"

  @DS1594_8
  Scenario: Editing an Amendment Reason display label does not change the underlying code
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And the display label for Amendment Reason code "OTHER" under Requested By "ASSURANCE" is updated to "Other (please specify offline)"
    When I request the amendment metadata reference lookup
    Then under Requested By "ASSURANCE" the reason with code "OTHER" has display label "Other (please specify offline)"
    And under Requested By "ASSURANCE" the reason code "OTHER" is unchanged

  @DS1594_9
  Scenario: Empty catalogue returns an empty Requested By list
    Given the amendment metadata reference data contains no active Requested By values
    When I request the amendment metadata reference lookup
    Then the lookup response contains an empty Requested By list

  @DS1594_10
  Scenario: Single-value catalogue is returned correctly
    Given the amendment metadata reference data contains only Requested By "PROVIDER" with reason "PROVIDER_ERROR"
    When I request the amendment metadata reference lookup
    Then the lookup response lists exactly one Requested By value with code "PROVIDER"
    And the Requested By value "PROVIDER" carries exactly one reason with code "PROVIDER_ERROR"

  @DS1594_11
  Scenario: "OTHER" is a controlled code with no free-text field
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When I request the amendment metadata reference lookup
    Then the reason "OTHER" under Requested By "CONTRACT_MANAGEMENT" has no free-text supporting field in the response
    And the reason "OTHER" under Requested By "ASSURANCE" has no free-text supporting field in the response

  @DS1594_12
  Scenario Outline: Reference row ids are generated as UUIDv7
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When I insert a new <table> row via the seed/load mechanism
    Then the generated id is a valid UUID
    And the generated id is UUIDv7

    Examples:
      | table                      |
      | requested_by_reference     |
      | amendment_reason_reference |

  @DS1594_13
  Scenario: Create audit columns are populated on seed/load
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When a new Requested By value with code "AUDITOR" is loaded by actor "seed-loader-service"
    Then the row for Requested By "AUDITOR" has created_by_user_id "seed-loader-service"
    And the row for Requested By "AUDITOR" has a non-null created_on timestamp
    And the row for Requested By "AUDITOR" has null updated_by_user_id
    And the row for Requested By "AUDITOR" has null updated_on

  @DS1594_14
  Scenario Outline: Update audit columns are populated when governed columns change
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And the Requested By value "PROVIDER" was originally created by actor "seed-loader-service"
    When the <column> for Requested By "PROVIDER" is updated by actor "ops-admin"
    Then the row for Requested By "PROVIDER" has updated_by_user_id "ops-admin"
    And the row for Requested By "PROVIDER" has a non-null updated_on timestamp
    And the row for Requested By "PROVIDER" has created_by_user_id "seed-loader-service" unchanged

    Examples:
      | column        |
      | display_label |
      | is_active     |
      | display_order |

  @DS1594_15
  Scenario: Historical amendment stability — code pairing survives label change
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And an amendment record persists the codes requested_by_code "PROVIDER" and amendment_reason_code "PROVIDER_ERROR"
    When the display label for Requested By "PROVIDER" is updated to "Provider (Legal Aid)"
    And the display label for Amendment Reason "PROVIDER_ERROR" under Requested By "PROVIDER" is updated to "Provider Error (rev)"
    Then the amendment record still references requested_by_code "PROVIDER"
    And the amendment record still references amendment_reason_code "PROVIDER_ERROR"
    And the amendment metadata reference lookup returns those codes paired together

  @DS1594_16
  Scenario: Same reason code can exist under multiple Requested By values independently
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When I request the amendment metadata reference lookup
    Then the reason "INCORRECT_MEANS_ASSESSMENT" is listed under Requested By "CONTRACT_MANAGEMENT"
    And the reason "INCORRECT_MEANS_ASSESSMENT" is listed under Requested By "ASSURANCE"
    And the reason "INCORRECT_MEANS_ASSESSMENT" is not listed under Requested By "PROVIDER"

