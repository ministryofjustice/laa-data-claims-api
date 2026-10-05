@Regression
@amendments
@dstew-1759
Feature: Amendment FSP request builder — built from post-amendment claim state

  # Jira: DSTEW-1759 (parent: DSTEW-1595 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  # Outbound: POST /api/v1/fee-calculation (Fee Scheme Platform)
  #
  # Once Step 13 (DSTEW-1758) has decided FSP is required, FeeSchemeRequestBuilder
  # builds the fee-calculation request from the fully-merged POST-amendment claim
  # state snapshot (FeeSchemeMapper.mapToFeeCalculationRequest(postAmendmentState)).
  # These scenarios assert the request that lands on the wire:
  #   * a changed FSP-input field carries the POST-amendment (provider-entered)
  #     value;
  #   * an omitted FSP-input field carries the CURRENT STORED value — proving the
  #     Step-2 sparse merge was applied before the request was built;
  #   * the request is built from the claim's submitted/provider-entered state, not
  #     from assessment or calculated-fee values.
  #
  # Observation: the builder is internal, so these scenarios assert the real
  # outbound fee-calculation request body captured by MockServer after a genuine
  # pricing amendment, rather than calling the builder directly.
  #
  # Seeded baseline (AmendableClaimFixture, Legal Help):
  #   feeCode=CAPA, caseStartDate=2025-07-01, caseConcludedDate=2025-07-31,
  #   netProfitCostsAmount=80, isVatApplicable=true. The FSP request JSON uses
  #   feeCode / startDate / caseConcludedDate / netProfitCosts / vatIndicator
  #   (FeeCalculationRequest), dates serialised ISO yyyy-MM-dd.
  #
  # ---------------------------------------------------------------------------
  # Reconciliation / coverage notes (verified 2026-10-01 against
  # FeeSchemeRequestBuilder + FeeSchemeMapper):
  #   * AC3 (explicit-null clear of an FSP-input field) is conditional in the story
  #     ("if a FSP-input field can validly be cleared") and is owned by the builder
  #     unit test FeeSchemeRequestBuilderTest#buildRequest_withNulls_mapsSafely,
  #     which proves a cleared FSP-input value maps to an explicit null per the FSP
  #     contract. Not re-driven here because reproducing a validly-clearable
  #     FSP-input field on the wire is fragile; recorded as covered, not descoped.
  #   * AC4 (assessment / calculated-fee values are not request inputs) is
  #     structurally guaranteed — the builder maps ONLY from postAmendmentState
  #     (the claim's submitted/provider-entered snapshot), never from the
  #     assessment or the baseline CalculatedFeeDetail — and is owned by the builder
  #     unit tests. DS1759_2 demonstrates the positive: the request's netProfitCosts
  #     comes from the claim's ClaimSummaryFee (80), not from any calculated-fee row.
  #
  # OUT OF SCOPE: FSP-required decision → DSTEW-1758; calling FSP / timeout →
  #               DSTEW-1760; response mapping → DSTEW-1761; persisting the
  #               calc-fee row → DSTEW-1762/1907; non-functional requirements.

  Background:
    Given the amendments feature flag is enabled

  # AC1 + AC2 — the worked example: a changed FSP-input field (case_start_date)
  # carries the post-amendment value; an omitted FSP-input field (fee_code) carries
  # the current stored value.
  @smoke @DS1759_1
  Scenario: Pricing amendment — changed FSP-input field uses the post-amendment value; omitted field uses the stored value
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed pricing amendment
    Then the amendment is accepted
    And exactly 1 outbound FSP call was made
    And the outbound FSP request field "startDate" equals "2025-08-04"
    And the outbound FSP request field "feeCode" equals "CAPA"

  # AC2 — sparse semantics were applied BEFORE the request was built: every
  # FSP-input field the amendment did not touch carries its current stored value.
  @DS1759_2
  Scenario: Pricing amendment — omitted FSP-input fields all carry current stored values (sparse merge applied)
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed pricing amendment
    Then the amendment is accepted
    And the outbound FSP request field "feeCode" equals "CAPA"
    And the outbound FSP request field "caseConcludedDate" equals "2025-07-31"
    And the outbound FSP request field "netProfitCosts" equals "80.0"
    And the outbound FSP request field "vatIndicator" equals "true"

  # AC1 — another representative pricing field: changing case_concluded_date (an
  # FSP-input, Legal-Help-amendable claim date) carries the post-amendment value on
  # the request, while the untouched FSP-input fields stay at their stored values.
  @DS1759_3
  Scenario: Pricing amendment on a second FSP-input field — post-amendment value is used, other inputs preserved
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a pricing amendment changing the claim field "case_concluded_date" to "15/07/2025"
    Then the amendment is accepted
    And exactly 1 outbound FSP call was made
    And the outbound FSP request field "caseConcludedDate" equals "2025-07-15"
    And the outbound FSP request field "startDate" equals "2025-07-01"
    And the outbound FSP request field "feeCode" equals "CAPA"



