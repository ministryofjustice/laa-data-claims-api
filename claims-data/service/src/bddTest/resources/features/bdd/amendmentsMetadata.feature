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
