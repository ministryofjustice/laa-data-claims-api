@Regression
@amendments
@dstew-2353
Feature: Amendment FSP repricing over real HTTP (MockServer)

  # Ticket: DSTEW-2353.
  #
  # Exercises the migrated FeeSchemePlatformRestClient repricing path end-to-end over real HTTP
  # against the shared MockServer (the client is no longer a @MockitoBean). A pricing-impacting
  # amendment (case_start_date) drives a single POST /api/v1/fee-calculation, so these scenarios
  # assert the call actually happened on the wire — and that an FSP failure maps to a rejected,
  # nothing-persisted outcome. This closes the coverage gap flagged on PR #475: without a pricing
  # scenario, the migrated client was never exercised over HTTP.

  Background:
    Given the amendments feature flag is enabled

  Scenario: Pricing amendment triggers a real FSP fee-calculation call and is accepted
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed pricing amendment
    Then the amendment is accepted
    And exactly 1 outbound FSP call was made

  Scenario: FSP repricing technical failure rejects the amendment and persists nothing
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will fail with HTTP 500
    When I submit a well-formed pricing amendment
    Then the amendment is rejected with HTTP 503 and amendment error code "TECHNICAL_ERROR_FSP_REPRICING_FAILURE"
    And exactly 1 outbound FSP call was made
    And no claim_amendment record was inserted for this claim by this attempt


