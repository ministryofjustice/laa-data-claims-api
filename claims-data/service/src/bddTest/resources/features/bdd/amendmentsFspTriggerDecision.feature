@Regression
@amendments
@classifier
@dstew-1758
Feature: Amendment Step 13 — FSP skip/continue decision from pricing classification

  # Jira: DSTEW-1758 (parent: DSTEW-1595 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #
  # Step 13 (AmendmentFspValidationStep) owns the SINGLE decision: does the
  # amendment proceed to FSP request building, or is FSP skipped entirely?
  # The decision is driven by whether any changed field is pricing-impacting:
  #   * no pricing-impacting change  → skip FSP (no outbound call, no new
  #                                    calculated_fee_detail row);
  #   * >= 1 pricing-impacting change → continue to FSP request building (one
  #                                     outbound fee-calculation call).
  #
  # This story is the DECISION. Siblings own the surrounding concerns:
  #   * Pricing rule source / classifier output (impacts_pricing + the
  #     source_rule_reference) → DSTEW-1757 (amendmentsFspPricingRule.feature).
  #   * FSP request building → DSTEW-1759; client mechanics / failure mapping →
  #     DSTEW-1760 / DSTEW-1761 (amendmentsFspRepricingHttp.feature @dstew-2353).
  #   * Rejecting assessed pricing / mixed amendments before FSP → DSTEW-1767
  #     (amendmentsAssessedPricingAndAmendability.feature).
  #
  # ---------------------------------------------------------------------------
  # Reconciliation against the shipped implementation (verified 2026-10-01 against
  # AmendmentFspValidationStep):
  #   * The shipped Step 13 derives the skip/continue decision from the amendment
  #     diff via FeeSchemeRequestField.impactsPricing(field, areaOfLaw) — the SAME
  #     FSP request-body field-map rule the DSTEW-1757/1766 classifier publishes as
  #     its source_rule_reference. So although the code "re-derives" rather than
  #     reading a persisted ChangedFieldClassification object, the DECISION is
  #     functionally identical to consuming the classifier output. AC5 ("uses
  #     classifier output, does not re-derive independently") is a code-structure
  #     concern owned by the unit tests (Test Notes); BDD asserts the observable
  #     contract — FSP called vs skipped.
  #   * Step 13 also skips FSP when any validation error was already collected
  #     (outcome-check gate), which is how an upstream rejection means "Step 13 is
  #     never reached and FSP is not called" is observed on the wire.
  #   * The proven pricing-impacting driver is a case_start_date change (an FSP
  #     request-body field that does not shift area-of-law); the proven non-pricing
  #     driver is a client_forename change. fee_code is avoided as the generic
  #     pricing driver because it can trip the fee-code/area-of-law gate first,
  #     which would skip FSP for the wrong reason.
  #
  # OUT OF SCOPE: classifier internals (DSTEW-1766); pricing rule source
  #               (DSTEW-1757); FSP request shape / client (DSTEW-1759..1761);
  #               non-functional requirements.

  Background:
    Given the amendments feature flag is enabled

  # AC1 — no changed field is pricing-impacting → Step 13 skips FSP. FSP is armed
  # and available, so "no call" proves the decision to skip, not an unavailable
  # dependency.
  @smoke @DS1758_1
  Scenario: Non-pricing amendment — Step 13 skips FSP even though FSP is available
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed non-pricing amendment
    Then the amendment is accepted
    And no outbound FSP call was made from the amendment harness
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt

  # AC2 — a pricing-impacting change → Step 13 proceeds to FSP request building,
  # observed as exactly one outbound fee-calculation call.
  @DS1758_2
  Scenario: Pricing amendment — Step 13 proceeds to FSP request building
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed pricing amendment
    Then the amendment is accepted
    And exactly 1 outbound FSP call was made

  # AC4 — "no unchanged input repricing" safeguard: a payload that restates an
  # FSP-input field at its current value (no effective change) alongside a
  # non-pricing change must NOT trigger FSP merely to refresh historical pricing.
  @DS1758_3
  Scenario: Unchanged FSP-input value — Step 13 does not reprice merely to refresh pricing
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit an amendment that restates an FSP-input field at its current value and changes only a non-pricing field
    Then the amendment is accepted
    And no outbound FSP call was made from the amendment harness
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt

  # AC3 — an assessed claim with a non-pricing-only amendment proceeds past the
  # assessed-pricing gate and Step 13 still skips FSP. (DSTEW-1767 @DS1767_6 owns
  # the assessed-gate angle; this asserts the Step-13 skip + no new calc-fee row.)
  # The amendment must be ACCEPTED (204): that proves it genuinely reached and
  # passed the Step 13 skip decision, rather than being rejected upstream (which
  # would also omit the assessed-pricing code, make no FSP call and add no fee row,
  # passing the assertions below for the wrong reason).
  @DS1758_4
  Scenario: Assessed claim + non-pricing amendment — Step 13 still skips FSP
    Given an original amendable claim exists with a valid pricing baseline
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    And the claim already has an assessment recorded
    And the classifier will mark the amendment "impacts_pricing" as "false"
    And an amendment changes only the field "client_surname" to "Jones"
    And the field "client_surname" is on the AaBC amendable-fields list for the claim's area of law
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted
    And the response does not contain error code "INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM"
    And no outbound FSP call was made from the amendment harness
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt

  # Required behaviour — "if upstream validation rejects the amendment, Step 13 is
  # never reached and FSP is not called." Here a pricing-impacting field
  # (case_start_date) is changed on an area of law where it is NOT amendable: the
  # field-amendability gate collects an error, so Step 13's outcome-check gate
  # skips FSP even though the changed field is pricing-impacting.
  @DS1758_5
  Scenario: Upstream rejection on a pricing-impacting field — Step 13 is not reached and FSP is not called
    Given an original claim exists with area of law "CRIME_LOWER"
    And the amendment PDA trigger will report "pda_relevant" as "true"
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the field "case_start_date" is NOT on the AaBC amendable-fields list for area of law "CRIME_LOWER"
    And an amendment changes only the field "case_start_date" to "01/05/2026"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the response contains error code "INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW"
    And no outbound FSP call was made from the amendment harness

