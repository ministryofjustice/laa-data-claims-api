@Regression
@amendments
@dstew-1761
Feature: Amendment FSP outcome mapping

  # Ticket: DSTEW-1761 (child of DSTEW-1595 — FSP Repricing Integration).
  #
  # Maps the four Fee Scheme Platform (FSP) repricing outcomes into amendment-flow outcomes using
  # the shared validation/error response style, and proves every failure outcome persists nothing:
  #   * business VALIDATION failure  -> HTTP 400 INVALID_FSP_VALIDATION_FAILURE, FSP message(s)
  #                                     surfaced unchanged
  #   * TECHNICAL failure (connection drop / unparseable body / configured read-timeout / opaque 5xx)
  #                                  -> HTTP 503 TECHNICAL_ERROR_FSP_REPRICING_FAILURE, safe user
  #                                     message, no FSP payload leaked
  #   * SUCCESS                       -> accepted (handed on to the DSTEW-1762 persistence step), i.e.
  #                                     success is NOT mis-mapped to a failure
  # The technical code is deliberately DISTINCT from the validation code (503 vs 400), satisfying the
  # "technical responses use distinct codes from validation failures" requirement across scenarios.
  #
  # Exercised end-to-end over real HTTP against the shared MockServer (the FeeSchemePlatformRestClient
  # is a real client, not a @MockitoBean). The amendment-path FSP read timeout is shortened to 800ms
  # by CucumberSpringConfiguration so the configured-timeout scenario trips deterministically; the
  # single-attempt/no-retry guarantee carried from DSTEW-1760 is asserted as "exactly 1 outbound FSP
  # call".
  #
  # The outcome-mapping internals are also unit/integration covered by DSTEW-2359
  # (ClaimAmendmentRepricingIntegrationTest). This feature is the AaBC-facing end-to-end contract.
  #
  # DESCOPED (non-functional, test-automation remit is functional scenarios only — see memory.md):
  #   * AC6 "safe FSP outcome/timing monitoring is emitted where supported" — an observability/NFR
  #     concern (metrics tags, timing histograms) not observable from the amendment response; owned by
  #     unit tests + the platform metrics wiring. The functional half (nothing persisted on every
  #     failure outcome, correct code/message) is asserted here.
  #   * Correlation-id / structured-log scraping for support detail — not observable from this harness;
  #     the user-safe-message-without-payload-leak half of that AC IS asserted (no-payload-leak below).

  Background:
    Given the amendments feature flag is enabled

  Scenario: FSP business validation failure rejects the amendment and surfaces the FSP message
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a business validation failure
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 400 and amendment error code "INVALID_FSP_VALIDATION_FAILURE"
    And the FSP outcome response contains the text "Fee code CLININQ is not eligible for matter type CRIME_LOWER"
    And exactly 1 outbound FSP call was made
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And no amendment event was recorded for this claim by this attempt

  Scenario: Multiple FSP validation failures surface every FSP message
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return multiple business validation failures
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 400 and amendment error code "INVALID_FSP_VALIDATION_FAILURE"
    And the FSP outcome response contains the text "Fee code CLININQ is not eligible for matter type CRIME_LOWER"
    And the FSP outcome response contains the text "Disbursement amount exceeds maximum allowed for this fee code"
    And the claim persisted state matches the pre-amendment state
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And no amendment event was recorded for this claim by this attempt

  Scenario: FSP connection drop maps to a controlled technical failure and persists nothing
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will drop the connection
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 503 and amendment error code "TECHNICAL_ERROR_FSP_REPRICING_FAILURE"
    And exactly 1 outbound FSP call was made
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And no amendment event was recorded for this claim by this attempt

  Scenario: FSP malformed response body maps to a controlled technical failure and persists nothing
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a malformed response body
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 503 and amendment error code "TECHNICAL_ERROR_FSP_REPRICING_FAILURE"
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And no amendment event was recorded for this claim by this attempt

  Scenario: FSP external-service timeout maps to a controlled technical failure with a single attempt
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will not respond within the amendment-path timeout
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 503 and amendment error code "TECHNICAL_ERROR_FSP_REPRICING_FAILURE"
    And exactly 1 outbound FSP call was made
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And no amendment event was recorded for this claim by this attempt

  Scenario: Technical failure returns a safe user message without leaking the FSP payload
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will fail with HTTP 500 carrying the body "SENSITIVE-FSP-INTERNAL-TRACE-abc123"
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 503 and amendment error code "TECHNICAL_ERROR_FSP_REPRICING_FAILURE"
    And the FSP outcome response contains the text "A technical error occurred while recalculating the fee. Please try again later."
    And the FSP outcome response does not contain the text "SENSITIVE-FSP-INTERNAL-TRACE-abc123"
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And no amendment event was recorded for this claim by this attempt

  Scenario: Successful FSP response maps to an accepted amendment, not a failure
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed pricing amendment
    Then the amendment is accepted
    And exactly 1 outbound FSP call was made

