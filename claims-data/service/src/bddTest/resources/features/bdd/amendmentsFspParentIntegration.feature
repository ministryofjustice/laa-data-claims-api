@Regression
@amendments
@fsp
@dstew-1595
Feature: FSP repricing — parent-level integration (ordering & atomicity)

  # Jira: DSTEW-1595 (parent: DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}  (DSTEW-1593)
  #   NB: the original draft cited "POST /api/v1/claims/{claimId}/amendments"; the
  #   shipped amendment endpoint is the submission-scoped PATCH above (updateClaim).
  #
  # Umbrella / parent story for the FSP repricing workstream. This parent file
  # covers ONLY the cross-cutting scenarios that no single child proves, following
  # the DSTEW-1646 precedent (amendmentsPdaParentIntegration.feature):
  #   * DS1595_1 — post-FSP persistence failure rolls back the amendment atomically.
  #   * DS1595_2 — assessed-claim pricing rejection short-circuits BEFORE any FSP call
  #                (crosses DSTEW-1767).
  #
  # RECONCILIATION with the draft (behaviour below matches shipped code and avoids
  # false-green seeding — documented, not silently changed):
  #   * The draft seeded both scenarios with the DSTEW-1757 phrase "an original claim
  #     exists with a valid pricing baseline", which provisions a THIN graph (matter
  #     type MAT01, no summary-fee / baseline CFD, no recorded baseline) used only for
  #     classifier spec-guards — a real amendment over it is rejected at metadata
  #     validation BEFORE FSP, so the FSP call and the post-FSP persistence step are
  #     never reached. That would pass the no-write assertions for the WRONG reason.
  #     Both scenarios now use the genuinely-amendable DSTEW-2301 harness seeding so
  #     the flow really reaches (DS1595_1) / is genuinely short-circuited at
  #     (DS1595_2) the FSP boundary.
  #   * DS1595_1 drives "I submit a well-formed pricing amendment" (a case_start_date
  #     change — a pricing-impacting FSP request-body field that does NOT shift the
  #     resolved Area of Law, so it isolates the repricing HTTP path from the AoL
  #     eligibility gate), then forces the persistence step to throw AFTER FSP has
  #     returned success. The atomicity is proven by the real DB no-write assertions;
  #     the post-FSP failure is additionally asserted as a real HTTP 500 + RFC 9457
  #     ProblemDetail (controlled terminal failure), replacing the DSTEW-1646-style
  #     spec-guard. "no partial fields" stays a spec-guard but is already backed by
  #     the real "claim persisted state matches the pre-amendment state" DB assertion.
  #   * DS1595_2 mirrors the merged DSTEW-1767 @DS1767_4 proven combination and asserts
  #     the real INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM code plus a real
  #     MockServer verification that no FSP fee-calculation call was made — replacing
  #     the draft's symbolic "rejected by the assessed-claim pricing gate" /
  #     "no outbound FSP call was made" spec-guards.
  #
  # OUT OF SCOPE (delegated to children — do NOT add here):
  #   * Rule source                → amendmentsFspPricingRule.feature (DSTEW-1757)
  #   * Skip / call decision       → DSTEW-1758
  #   * FSP request body           → DSTEW-1759
  #   * Single-attempt timeout     → DSTEW-1760
  #   * Outcome mapping            → DSTEW-1761
  #   * Calculated-fee / is_price_changed handoff → DSTEW-1762
  #   * Assessed-claim rejection internals → DSTEW-1767

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"
    And the PDA service will respond "authorised" within the amendment-path timeout

  @DS1595_1
  Scenario: Post-FSP persistence failure rolls back the amendment atomically
    Given a fresh amendable claim on a legal-help submission at version 0
    And the FSP service will return a valid fee calculation for the amendment
    And the amendment persistence step will fail after FSP has returned success
    When I submit a well-formed pricing amendment
    Then the endpoint responds with a controlled post-FSP persistence failure
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And the claim persisted state matches the pre-amendment state
    And no partial amendment fields are visible on subsequent reads

  @DS1595_2
  Scenario: Assessed-claim pricing rejection short-circuits before any FSP call (crosses DSTEW-1767)
    Given an original amendable claim exists with a valid pricing baseline
    And the claim already has an assessment recorded
    And the classifier will mark the amendment "impacts_pricing" as "true"
    And an amendment changes only the field "fee_code" to "FEE-B"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the response contains error code "INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM"
    And no outbound FSP call was made from the amendment harness
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And the claim persisted state matches the pre-amendment state

