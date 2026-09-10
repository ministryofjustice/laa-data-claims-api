@Regression
@amendments
@pda
@dstew-1774
Feature: PDA re-validation — outcome mapping (validation messages & terminal technical)

  # Jira: DSTEW-1774 (parent: DSTEW-1646 → DSTEW-1999)
  # Endpoints: POST /api/v1/bulk-submissions, GET /api/v1/validation-messages,
  #            GET /api/v1/bulk-submissions/{id}/summary
  #
  # PDA "no matching schedule for Area of Law" → INVALID_AREA_OF_LAW_FOR_PROVIDER
  # PDA "category of law not authorised"       → INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER
  # Validation messages sit in the shared Step 12 multi-message response (DSTEW-1770).
  # Technical failures (HTTP 5xx, connection drop, schema/parse error, external-service
  # timeout) → terminal controlled failure TECHNICAL_ERROR_PROVIDER_DETAILS_API.
  # Terminal wins over any earlier collected validation messages in the same attempt.
  # Every PDA failure outcome is no-save to orchestration.
  # Logs/monitoring carry safe correlation/outcome/timing only — never payload or financial.
  #
  # Coverage note: stub infrastructure exists in MockServerIntegrationTest lines 367-414
  # (HTTP 5xx, connection drop, malformed body, delayed response) but no integration
  # test currently invokes these stubs and asserts the returned error codes — all
  # scenarios in this file remain in scope.
  #
  # OUT OF SCOPE: Call mechanics → amendmentsPdaCallMechanics.feature;
  #               Parent-flow integration → amendmentsPdaParentIntegration.feature.

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"

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
    Given an original claim exists with feeCode "ASSA" and officeCode "OFC-A07" and effectiveDate "2025-04-01"
    And an amendment updates the claim to feeCode "IMCA" and effectiveDate "2026-04-01"
    And the PDA service will <pdaBehaviour>
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the PDA outcome handed to orchestration is marked as no-save
    And no amendment record, diff, calculated-fee child row, event or claim-state update was committed
    And the claim persisted state matches the pre-amendment state

    Examples:
      | failureCategory    | pdaBehaviour                                       |
      | validation failure | return a schedule set with no matching Area of Law |
      | technical failure  | respond with HTTP 500                              |
      | external timeout   | not respond before 10 seconds                      |

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

