@Regression
@amendments
@metadata
@dstew-1765
Feature: Amendment metadata — submit-time validation (Requested By / Amendment Reason / Entra UUID)

  # Jira: DSTEW-1765 (parent: DSTEW-1999)
  # Endpoints: POST /api/v1/bulk-submissions, GET /api/v1/validation-messages,
  #            GET /api/v1/bulk-submissions/{id}/summary
  #
  # Requested By and Amendment Reason are mandatory and must be stable codes.
  # Requested By must be active in Reference Data.
  # Amendment Reason must be active AND valid for the submitted Requested By.
  # Entra user id validated as UUID structurally ONLY; no user-existence lookup.
  # Metadata failures feed the shared Step 12 multi-message response (DSTEW-1770).
  # Reference-data source unavailable → controlled terminal failure
  #   TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA; no save.
  #
  # Fixture used below (placeholder-code set, to reconcile with BC-574 names):
  #   Requested By: RB_PROVIDER (active), RB_CASEWORKER (active), RB_LEGACY (inactive)
  #   Amendment Reason: AR_FEE_CORR (active, valid for RB_PROVIDER),
  #                     AR_CATEGORY_FIX (active, valid for RB_CASEWORKER),
  #                     AR_RETIRED (inactive, was valid for RB_PROVIDER)
  #
  # OUT OF SCOPE: Feature flag → amendmentsFeatureFlag.feature;
  #               Reference-data lookup contract → amendmentsMetadataReferenceLookup.feature.

  @smoke @DS1765_1
  Scenario: Valid metadata does not raise any metadata validation error
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no metadata validation error is raised
    And the submitted metadata values are available for persistence

  @DS1765_2
  Scenario Outline: Requested By <case> is rejected with <expectedCode>
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an amendment with metadata
      | requestedByCode   | amendmentReasonCode | submittingUserId                     |
      | <requestedByCode> | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment and wait for the event service to complete amendment validation
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
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode   | submittingUserId                     |
      | RB_PROVIDER     | <amendmentReasonCode> | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment and wait for the event service to complete amendment validation
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
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_CATEGORY_FIX     | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                                |
      | INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY |
    And no amendment state was committed

  @DS1765_5
  Scenario: Amendment Reason is accepted when paired with its valid Requested By
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_CASEWORKER   | AR_CATEGORY_FIX     | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no metadata validation error is raised

  @DS1765_6
  Scenario Outline: Submitting user id "<userId>" is <result>
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId |
      | RB_PROVIDER     | AR_FEE_CORR         | <userId>         |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then <expectedAssertion>
    And no existence check against the identity provider was performed

    Examples:
      | userId                               | result               | expectedAssertion                                                                              |
      | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 | accepted             | no metadata validation error is raised                                                         |
      | not-a-uuid                           | rejected as non-UUID | the amendment is rejected with a validation message with code "INVALID_USER_IDENTIFIER_FORMAT" |
      | 8f14e45f-ceea-467a-b3c0              | rejected as non-UUID | the amendment is rejected with a validation message with code "INVALID_USER_IDENTIFIER_FORMAT" |
      |                                      | rejected as non-UUID | the amendment is rejected with a validation message with code "INVALID_USER_IDENTIFIER_FORMAT" |

  @DS1765_7
  Scenario: Multiple metadata errors are surfaced together in one Step 12 response
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId |
      |                 |                     | not-a-uuid       |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors in any order
      | Error Code                       |
      | INVALID_REQUESTED_BY_MISSING     |
      | INVALID_AMENDMENT_REASON_MISSING |
      | INVALID_USER_IDENTIFIER_FORMAT   |
    And each error is returned in the shared Step 12 multi-message response
    And no amendment state was committed

  @DS1765_8
  Scenario: Metadata validation does not perform display-name or current-user lookups
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no display-name lookup was performed against reference data
    And no existence check against the identity provider was performed

  @DS1765_9
  Scenario: Reference data unavailable returns a controlled technical failure and no amendment is saved
    Given the amendments feature flag is enabled
    And an amendment with metadata
      | requestedByCode | amendmentReasonCode | submittingUserId                     |
      | RB_PROVIDER     | AR_FEE_CORR         | 8f14e45f-ceea-467a-b3c0-38f89b0e07a1 |
    And the amendment metadata reference-data source is unavailable
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the endpoint responds with a controlled terminal failure "TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA"
    And no amendment state was committed

