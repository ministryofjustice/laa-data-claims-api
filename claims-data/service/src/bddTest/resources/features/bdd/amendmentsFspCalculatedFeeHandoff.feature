@Regression
@amendments
@dstew-1762
Feature: Amendment FSP calculated-fee persistence handoff

  # Ticket: DSTEW-1762 (child of DSTEW-1595 — FSP Repricing Integration).
  #
  # On a SUCCESSFUL FSP repricing, prepares the amendment-linked calculated_fee_detail row and the
  # FSP-sourced diff entries, to be committed by the single atomic save owned by DSTEW-1771 and
  # written by DSTEW-1907. Verified end-to-end over the amendment PATCH against the real DB:
  #   * is_price_changed = true  when the FSP total differs from the previous calculated total
  #   * is_price_changed = false when the FSP total equals the previous calculated total (the row is
  #                        still prepared so consumers can see FSP was called)
  #   * the new row is linked to the committed amendment via claim_amendment_id
  #   * FSP-calculated monetary consequences appear in the diff with change_source = "FSP"
  #     (distinct from the provider-requested change_source = "REQUESTED")
  #   * the previous calculated_fee_detail row is retained and not directly edited
  #   * when the atomic save rolls back (final-save OCC conflict), the prepared FSP row is discarded
  #     because it is never committed independently
  #
  # The previous calculated total is set explicitly per scenario because the DSTEW-2301 fixture
  # seeds the baseline calculated_fee_detail with a null total; setting it makes the is_price_changed
  # comparison deterministic (worked examples: 100.00 -> 125.00 changed, 100.00 -> 100.00 unchanged).
  #
  # Out of scope (owned elsewhere, not re-asserted here): physical schema / indexes (DSTEW-1659);
  # transaction boundary + OCC orchestration (DSTEW-1771, also integration-covered by
  # AmendmentCommitRollbackIntegrationTest); read-side latest-row selection (DSTEW-1644); history API
  # exposure (history/read-side stories). Field-by-field FSP->entity projection + the is_price_changed
  # computation are unit-covered by FeeSchemeHandoffFactoryTest; this feature is the AaBC-facing
  # end-to-end contract.

  Background:
    Given the amendments feature flag is enabled

  Scenario: Monetary change — prepared row is linked, is_price_changed true, FSP diff entry present, previous row retained
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    And the claim's previous calculated fee total is 100.00
    And the FSP service will return a fee calculation with total amount 125.00
    When I submit a well-formed pricing amendment
    Then the amendment is accepted
    And the latest calculated_fee_detail row for the claim has is_price_changed true
    And the latest calculated_fee_detail row is linked to the committed amendment
    And the committed amendment diff contains a change_source "FSP" entry for field "fee.totalAmount"
    And the committed amendment diff contains a change_source "REQUESTED" entry for field "claim.caseStartDate"
    And the claim has exactly 2 calculated_fee_detail rows
    And the previous calculated_fee_detail row is retained unchanged

  Scenario: No monetary change — row is still prepared and linked, is_price_changed false
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    And the claim's previous calculated fee total is 100.00
    And the FSP service will return a fee calculation with total amount 100.00
    When I submit a well-formed pricing amendment
    Then the amendment is accepted
    And the latest calculated_fee_detail row for the claim has is_price_changed false
    And the latest calculated_fee_detail row is linked to the committed amendment
    And the claim has exactly 2 calculated_fee_detail rows

  Scenario: Atomic-save rollback after successful FSP — the prepared calculated-fee row is discarded
    Given a fresh amendable claim on a legal-help submission at version 0
    And the FSP service will return a valid fee calculation for the amendment
    And the claim's previous calculated fee total is 100.00
    And the FSP service will return a fee calculation with total amount 125.00
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And the claim has exactly 1 calculated_fee_detail rows

