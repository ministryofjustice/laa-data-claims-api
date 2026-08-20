@Regression
@amendments
@classifier
@dstew-1757
Feature: Amendment changed-field classifier — FSP pricing rule (impacts_pricing)

  # Jira: DSTEW-1757 (parent: DSTEW-1595 → DSTEW-1999)
  # Feeds: DSTEW-1766 classifier via source_rule_reference.
  # Endpoints: POST /api/v1/bulk-submissions, GET /api/v1/bulk-submissions/{id}/summary
  #
  # Canonical rule source = fields on the FSP fee-calculation request body.
  # FSP request-body rule WINS over supporting artefacts (AaBC amendable-fields,
  # working pricing-impact views) whenever they disagree.
  # Emits a traceable source_rule_reference.
  #
  # OUT OF SCOPE: PDA trigger → amendmentsPdaTrigger.feature;
  #               Step 13 FSP-call decision → DSTEW-1758;
  #               FSP request building / calling → DSTEW-1758/DSTEW-1759;
  #               Assessed-claim pricing rejection → DSTEW-1767.

  Background:
    Given the amendments feature flag is enabled
    And the FSP fee-calculation request-body field map is the authoritative pricing rule source

  @smoke @DS1757_1
  Scenario Outline: FSP pricing — changed field on the FSP request body is pricing-impacting
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "<field>" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference identifies the FSP request-body rule source
    And the classifier source_rule_reference includes the changed field "<field>"

    Examples:
      | field                     |
      | fee_code                  |
      | case_start_date           |
      | case_concluded_date       |
      | representation_order_date |
      | ufn                       |
      | office_code               |

  @DS1757_2
  Scenario Outline: FSP pricing — changed field NOT on the FSP request body is non-pricing
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "<field>" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"
    And the classifier source_rule_reference indicates no pricing-impacting field changed

    Examples:
      | field            |
      | client_surname   |
      | client_forename  |
      | client_reference |

  @DS1757_3
  Scenario: FSP pricing — FSP-input field with an unchanged effective value is not pricing-impacting
    Given an original claim exists with feeCode "FEE-A"
    And an amendment payload includes feeCode "FEE-A" unchanged and updates only the clientSurname to "Smith"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"

  @DS1757_4
  Scenario: FSP pricing — a single pricing-impacting change amongst non-pricing changes still classifies as pricing-impacting
    Given an original claim exists with a valid pricing baseline
    And an amendment changes the following fields
      | field           | newValue |
      | client_surname  | Smith    |
      | client_forename | Ada      |
      | fee_code        | FEE-B    |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference includes the changed field "fee_code"

  @DS1757_5
  Scenario: FSP pricing — only non-pricing changes → impacts_pricing false
    Given an original claim exists with a valid pricing baseline
    And an amendment changes the following fields
      | field            | newValue |
      | client_surname   | Smith    |
      | client_forename  | Ada      |
      | client_reference | CLI-999  |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"

  @DS1757_6
  Scenario: FSP pricing — supporting artefact says pricing, FSP body says no → FSP wins (false)
    Given a supporting artefact classifies the field "supporting_only_field" as pricing-impacting
    But the field "supporting_only_field" does not appear in the FSP fee-calculation request body
    And an original claim exists with a valid pricing baseline
    And an amendment changes only the field "supporting_only_field" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"
    And the classifier source_rule_reference identifies the FSP request-body rule source

  @DS1757_7
  Scenario: FSP pricing — supporting artefact says non-pricing, FSP body says yes → FSP wins (true)
    Given a supporting artefact classifies the field "fsp_only_field" as non-pricing
    But the field "fsp_only_field" appears in the FSP fee-calculation request body
    And an original claim exists with a valid pricing baseline
    And an amendment changes only the field "fsp_only_field" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference identifies the FSP request-body rule source
    And the classifier source_rule_reference includes the changed field "fsp_only_field"

  @DS1757_8
  Scenario: FSP pricing — traceable source_rule_reference for pricing-impacting result
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "fee_code" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference is "FSP_REQUEST_BODY_FIELD_CHANGED"
    And the classifier source_rule_reference identifies the FSP request-body rule source

  @DS1757_9
  Scenario: FSP pricing — traceable source_rule_reference for non-pricing result
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "client_surname" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"
    And the classifier source_rule_reference is "NO_FSP_REQUEST_BODY_FIELD_CHANGED"

