@Regression
@amendments
@pda
@dstew-1646
Feature: PDA re-validation — parent-level integration (ordering & atomicity)

  # Jira: DSTEW-1646 (parent: DSTEW-1999)
  # Endpoints: POST /api/v1/bulk-submissions, GET /api/v1/bulk-submissions/{id}/summary,
  #            GET /api/v1/claims/{id}
  #
  # Parent story covering ordering/atomicity guarantees around PDA:
  #   * Eligibility / stale-version rejections short-circuit BEFORE PDA is called.
  #   * Post-PDA persistence failure rolls back atomically.
  #
  # Coverage review (2026-08-05) — only the two unique-to-parent scenarios remain.
  # The other 6 originally drafted (@DS1646_1..4, _6, _7) duplicated child stories
  # DSTEW-1773 / DSTEW-1774 and were dropped.
  #
  # OUT OF SCOPE: PDA call mechanics → amendmentsPdaCallMechanics.feature;
  #               PDA outcome mapping → amendmentsPdaOutcomeMapping.feature.

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"

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

