@Regression
@amendments
@dstew-harness-canary
Feature: Amendment BDD harness canary

  # Ticket: DSTEW-2301.
  #
  # Guards the harness scaffolding itself, NOT any AaBC business story. If this
  # scenario ever goes red on main it means the shared amendment BDD harness
  # (fixture, mocks, reset hook, shared step glue) has regressed and every
  # downstream amendment BDD delivery is on shaky ground until this canary is
  # green again.
  #
  # Do NOT tag this to any DSTEW-1xxx story number.

  Background:
    Given the amendments feature flag is enabled

  Scenario: Non-pricing amendment on a fresh valid claim returns 2xx and inserts one amendment row
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed non-pricing amendment
    Then the amendment is accepted
    And exactly one claim_amendment row was inserted for this claim
    And claim.version is now 1
    And claim.is_amended is true
    And no outbound FSP call was made

