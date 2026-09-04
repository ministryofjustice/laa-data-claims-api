@Regression
@amendments
@eligibility
@dstew-1764
Feature: Amendment eligibility gate — claim.status must be VALID

  # Jira: DSTEW-1764 (parent: DSTEW-1593 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #
  # Gate ordering: retrieval (DSTEW-1763) → eligibility (this ticket) →
  #                metadata / duplicate / PDA / FSP / persistence.
  # Distinct error codes:
  #   * voided claim         → INVALID_VOIDED_CLAIM_NOT_AMENDABLE
  #   * any other non-VALID  → INVALID_CLAIM_STATE_NOT_AMENDABLE (echoes the
  #                            current claim.status where available)
  #
  # OUT OF SCOPE: Assessed-claim pricing → DSTEW-1767;
  #               OCC/version → amendmentsRequestContract.feature +
  #               amendmentsFinalSaveGuard.feature;
  #               Field/metadata/duplicate/PDA/FSP → their own tickets.
  #
  # Coverage review (2026-08-11): the eligibility implementation
  # (`ClaimStatusValidationStep`) already exists in production code; the gap
  # DSTEW-1764 fills is the missing BDD coverage of the gate + its error
  # codes. Sibling `amendmentsPdaParentIntegration.feature @DS1646_1` asserts
  # the ordering guarantee at a higher level (early rejection short-circuits
  # PDA) but doesn't cover the void-vs-other-status distinction or the error
  # codes.

  Background:
    Given the amendments feature flag is enabled
    And the amendment metadata reference-data source is available

  @smoke @DS1764_1
  Scenario: Eligible — claim.status "VALID" proceeds past the eligibility gate
    Given an original claim exists with claim.status "VALID"
    And a well-formed amendment payload for that claim
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the eligibility gate does not reject the amendment
    And no eligibility error code is present in the response

  @DS1764_2
  Scenario: Voided — rejected with INVALID_VOIDED_CLAIM_NOT_AMENDABLE (and NOT with the generic not-amendable code)
    Given an original claim exists with claim.status "VOIDED"
    And a well-formed amendment payload for that claim
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following eligibility errors
      | Error Code                          |
      | INVALID_VOIDED_CLAIM_NOT_AMENDABLE  |
    And the response does not contain error code "INVALID_CLAIM_STATE_NOT_AMENDABLE"
    And no outbound PDA call was made
    And no outbound FSP call was made
    And no eligibility amendment state was committed

  @DS1764_3
  Scenario Outline: Non-VALID (non-VOIDED) status "<status>" is rejected with INVALID_CLAIM_STATE_NOT_AMENDABLE
    Given an original claim exists with claim.status "<status>"
    And a well-formed amendment payload for that claim
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following eligibility errors
      | Error Code                        |
      | INVALID_CLAIM_STATE_NOT_AMENDABLE |
    And the response includes the current claim.status "<status>"
    And no outbound PDA call was made
    And no outbound FSP call was made
    And no eligibility amendment state was committed

    Examples:
      | status            |
      | READY_TO_PROCESS  |
      | INVALID           |

  @DS1764_4
  Scenario: Eligibility gate short-circuits before metadata, duplicate, PDA and FSP steps
    Given an original claim exists with claim.status "VOIDED"
    And an amendment payload that would also fail metadata validation and duplicate checks
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following eligibility errors
      | Error Code                          |
      | INVALID_VOIDED_CLAIM_NOT_AMENDABLE  |
    And the response does not contain any metadata validation error code
    And the response does not contain any duplicate check error code
    And no outbound PDA call was made
    And no outbound FSP call was made
    And no eligibility amendment state was committed

  @DS1764_5
  Scenario: Retrieval failure precedes eligibility — a non-existent claim id does not surface an eligibility error
    Given no amendable claim exists for claim id "00000000-0000-0000-0000-000000000000"
    And a well-formed amendment payload for that claim id
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the response does not contain error code "INVALID_VOIDED_CLAIM_NOT_AMENDABLE"
    And the response does not contain error code "INVALID_CLAIM_STATE_NOT_AMENDABLE"
    And no outbound PDA call was made
    And no outbound FSP call was made
