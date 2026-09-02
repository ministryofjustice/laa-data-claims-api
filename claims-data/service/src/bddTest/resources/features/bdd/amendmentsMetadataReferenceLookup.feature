@Regression
@amendments
@metadata
@dstew-1594
Feature: Amendment metadata — reference-data lookup & governance

  # Jira: DSTEW-1594 (parent: DSTEW-1999)
  # Endpoint: GET /api/v1/reference/amendment-metadata
  #
  # Governed reference data for Requested By + party-scoped Amendment Reason.
  # Lookup returns ACTIVE values only, in configured display_order.
  # Inactive values disappear from the lookup without hard-delete.
  # Display-label edits do NOT change the underlying code (historical stability
  # for persisted amendment records).
  # Values can be added / updated WITHOUT a service redeploy.
  # Reference row ids are UUIDv7.
  # Create/update audit columns are populated by seed/load and updates.
  #
  # BC-574 seed fixture:
  #   PROVIDER              (10)  Provider
  #     PROVIDER_ERROR                     (10) Provider error
  #     CASE_REOPENED_REBILLED             (20) Case re-opened and being billed again later
  #     RECOVERY_FROM_CLIENT_OR_OTHER_SIDE (30) Money recovered from client and/or other side (inc. stat charge)
  #   CONTRACT_MANAGEMENT   (20)  Contract management
  #     INCORRECT_MEANS_ASSESSMENT (10) Incorrect means assessment
  #     OTHER                      (20) Other
  #   ASSURANCE             (30)  Assurance
  #     INCORRECT_MEANS_ASSESSMENT (10) Incorrect means assessment
  #     OTHER                      (20) Other
  #
  # OUT OF SCOPE: Submit-time metadata validation → amendmentsMetadataValidation.feature.

  @smoke @DS1594_1
  Scenario: Lookup returns active Requested By values in display order
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When I request the amendment metadata reference lookup
    Then the lookup response lists the following Requested By values in order
      | code                | display_label       | display_order |
      | PROVIDER            | Provider            | 10            |
      | CONTRACT_MANAGEMENT | Contract management | 20            |
      | ASSURANCE           | Assurance           | 30            |

  @DS1594_2
  Scenario: Each Requested By value carries only its own reasons in display order
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    When I request the amendment metadata reference lookup
    Then the Requested By value "PROVIDER" carries the following reasons in order
      | code                               | display_label                                                    | display_order |
      | PROVIDER_ERROR                     | Provider error                                                   | 10            |
      | CASE_REOPENED_REBILLED             | Case re-opened and being billed again later                      | 20            |
      | RECOVERY_FROM_CLIENT_OR_OTHER_SIDE | Money recovered from client and/or other side (inc. stat charge) | 30            |
    And the Requested By value "CONTRACT_MANAGEMENT" carries the following reasons in order
      | code                       | display_label              | display_order |
      | INCORRECT_MEANS_ASSESSMENT | Incorrect means assessment | 10            |
      | OTHER                      | Other                      | 20            |
    And the Requested By value "ASSURANCE" carries the following reasons in order
      | code                       | display_label              | display_order |
      | INCORRECT_MEANS_ASSESSMENT | Incorrect means assessment | 10            |
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
      | INCORRECT_MEANS_ASSESSMENT | Incorrect means assessment | 10            |
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
      | PROVIDER_ERROR                     | Provider error                                                   | 10            |
      | CASE_REOPENED_REBILLED             | Case re-opened and being billed again later                      | 20            |
      | RECOVERY_FROM_CLIENT_OR_OTHER_SIDE | Money recovered from client and/or other side (inc. stat charge) | 30            |
      | OTHER                              | Other                                                            | 40            |

  @DS1594_7
  Scenario: Editing a display label does not change the underlying code
    Given the amendment metadata reference data has been seeded with the BC-574 defaults
    And the display label for Requested By code "PROVIDER" is updated to "Provider (legal aid)"
    When I request the amendment metadata reference lookup
    Then the Requested By value with code "PROVIDER" has display label "Provider (legal aid)"
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
    When the display label for Requested By "PROVIDER" is updated to "Provider (legal aid)"
    And the display label for Amendment Reason "PROVIDER_ERROR" under Requested By "PROVIDER" is updated to "Provider error (rev)"
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

