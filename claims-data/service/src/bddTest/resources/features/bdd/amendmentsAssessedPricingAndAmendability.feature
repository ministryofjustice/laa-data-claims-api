@Regression
@amendments
@classifier
@dstew-1767
Feature: Amendment gates — field amendability & assessed-claim pricing restriction

  # Jira: DSTEW-1767 (parent: DSTEW-1593 → DSTEW-1999)
  # Endpoint: POST /api/v1/claims/{claimId}/amendments  (DSTEW-1593)
  #
  # Consumes ChangedFieldClassification output from DSTEW-1766 to apply two
  # amendment-specific gates that do NOT need fee-code detail lookup:
  #   * Field amendability   → INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW
  #     (collected as field-level validation messages; aggregate via Step 12).
  #   * Assessed-claim price → INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM
  #     (whole-amendment rejection BEFORE any FSP call; no partial save).
  #
  # Field amendability source of truth: signed-off AaBC artefact
  # "Amend MVP - fields for amendment", scoped per Area of Law.
  #
  # Coverage review (2026-08-11): no existing implementation or tests found.
  # Sibling `amendmentsFspParentIntegration.feature @DS1595_2` already asserts
  # the assessed-claim gate short-circuits FSP from the FSP-parent angle;
  # this file owns the detailed error-code + field-level assertions.
  #
  # OUT OF SCOPE: Fee-code → Area-of-Law + fee-code-driven mandatory field
  # validation → DSTEW-1768; FSP trigger / call → DSTEW-1758..1761;
  # classifier internals → DSTEW-1766.

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"
    And the PDA service will respond "authorised" within the amendment-path timeout

  # ============================================================================
  # Field amendability (INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW)
  # ============================================================================

  @smoke @DS1767_1
  Scenario: Amendable field for the claim's Area of Law does not raise a field-amendability error
    Given an original claim exists with area of law "CRIME_LOWER"
    And the field "client_surname" is on the AaBC amendable-fields list for area of law "CRIME_LOWER"
    And an amendment changes only the field "client_surname" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no field-amendability error is raised for the field "client_surname"

  @DS1767_2
  Scenario Outline: Non-amendable field "<field>" for area of law "<areaOfLaw>" is collected as INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW
    Given an original claim exists with area of law "<areaOfLaw>"
    And the field "<field>" is NOT on the AaBC amendable-fields list for area of law "<areaOfLaw>"
    And an amendment changes only the field "<field>" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following field-level errors
      | field   | Error Code                                    |
      | <field> | INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW   |
    And no amendment state was committed

    Examples:
      | areaOfLaw   | field                     |
      | CRIME_LOWER | case_start_date           |
      | LEGAL_HELP  | representation_order_date |

  @DS1767_3
  Scenario: Multiple non-amendable fields are aggregated in the Step 12 response
    # Realigned to MEDIATION (DSTEW-1767 realignment): representation_order_date and
    # unique_file_number are both genuinely non-amendable for MEDIATION in the current
    # AmendableClaimFields registry, giving two INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW
    # errors. The amendable client_surname change is included to prove the aggregation collects
    # ONLY the non-amendable fields — no error is raised for the amendable one. (An earlier
    # fabricated INVALID_FIELD_VALUE expectation was removed as a false-green: nothing injected it,
    # so the strict UAT assertion had no real source — see PR #478 Copilot review.)
    Given an original claim exists with area of law "MEDIATION"
    And the fields "representation_order_date" and "unique_file_number" are NOT on the AaBC amendable-fields list for area of law "MEDIATION"
    And an amendment changes the following fields
      | field                     | newValue   |
      | representation_order_date | 2026-05-01 |
      | ufn                       | 010426/998 |
      | client_surname            | Amended    |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors in any order
      | Error Code                                  |
      | INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW |
      | INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW |
    And each error is returned in the shared Step 12 multi-message response
    And no amendment state was committed

  # ============================================================================
  # Assessed-claim pricing gate (INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM)
  # ============================================================================

  @DS1767_4
  Scenario: Assessed claim + pricing-impacting amendment is rejected before any FSP call
    Given an original amendable claim exists with a valid pricing baseline
    And the claim already has an assessment recorded
    And the classifier will mark the amendment "impacts_pricing" as "true"
    And an amendment changes only the field "fee_code" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                                  |
      | INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM |
    And no outbound FSP call was made from the amendment harness
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And the claim persisted state matches the pre-amendment state
    And no amendment-related event was published for this attempt

  @DS1767_5
  Scenario: Unassessed claim + pricing-impacting amendment is NOT rejected by this gate
    Given an original amendable claim exists with a valid pricing baseline
    And the claim has no assessment recorded
    And the classifier will mark the amendment "impacts_pricing" as "true"
    And an amendment changes only the field "fee_code" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the response does not contain error code "INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM"

  @DS1767_6
  Scenario: Assessed claim + non-pricing-only amendment proceeds past this gate and FSP is not called
    Given an original amendable claim exists with a valid pricing baseline
    And the claim already has an assessment recorded
    And the classifier will mark the amendment "impacts_pricing" as "false"
    And an amendment changes only the field "client_surname" to "Jones"
    And the field "client_surname" is on the AaBC amendable-fields list for the claim's area of law
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the response does not contain error code "INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM"
    And no outbound FSP call was made from the amendment harness

  @DS1767_7
  Scenario: Assessed-pricing rejection wins over a co-occurring field-amendability failure — pricing code is present, no partial save
    Given an original claim exists with area of law "CRIME_LOWER"
    And the claim already has an assessment recorded
    And the classifier will mark the amendment "impacts_pricing" as "true"
    And the field "case_start_date" is NOT on the AaBC amendable-fields list for area of law "CRIME_LOWER"
    And an amendment changes the following fields
      | field                     | newValue   |
      | fee_code                  | FEE-B      |
      | case_start_date           | 2026-05-01 |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the response contains error code "INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM"
    And no outbound FSP call was made from the amendment harness
    And no amendment state was committed

