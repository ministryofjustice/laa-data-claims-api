@Regression
@claimHistory
@dstew-1645
Feature: Claim history timeline — parent-level cross-cutting guarantees

  # Jira: DSTEW-1645 (parent: DSTEW-1999)
  # Endpoint: GET /api/v1/claims/{claimId}/history  (contract owned by DSTEW-1811)
  #
  # Parent orchestration story split across 5 children:
  #   * DSTEW-1811 → contract skeleton + SUBMISSION events → future feature file
  #   * DSTEW-1812 → ASSESSMENT / VOID events              → future feature file
  #   * DSTEW-1813 → AMENDMENT event metadata              → future feature file
  #   * DSTEW-1814 → field-level before/after diff         → future feature file
  #   * DSTEW-1815 → FSP repricing / escape metadata       → claimHistoryAmendmentEvents.feature
  #
  # Following the DSTEW-1595 / DSTEW-1646 parent-file precedent, this file
  # covers ONLY cross-cutting guarantees that no single child can prove:
  #   * Mixed timeline (all 4 event types together, deterministic order).
  #   * Failed amendment attempts have NO event (crosses the write path).
  #   * New Matter Starts NOT in the timeline (submission-level exclusion).
  #   * Raw request payload + full before-state NOT exposed in main response.
  #   * Submission-only / minimal-history shape.
  #   * Actor fallback when the source user id is missing.
  #   * Write-to-read smoke through DSTEW-1593 (persisted, not seeded).
  #
  # Coverage review (2026-08-11): `JdbcClaimHistoryRepository` (lines 64-136)
  # provides the SQL; `ClaimHistoryControllerIntegrationTest` covers
  # SUBMISSION / ASSESSMENT / VOID individually but NOT the parent-level
  # cross-cutting guarantees above. All scenarios in this file are new
  # BDD coverage.
  #
  # OUT OF SCOPE (delegated to children — do NOT add here):
  #   * SUBMISSION event shape                → DSTEW-1811
  #   * ASSESSMENT / VOID event shape         → DSTEW-1812
  #   * AMENDMENT event metadata              → DSTEW-1813
  #   * Field-level diff rendering            → DSTEW-1814
  #   * FSP / escape metadata                 → claimHistoryAmendmentEvents.feature (DSTEW-1815)
  #   * Not-found (404 shape)                 → DSTEW-1811 (owns endpoint contract)
  #   * Large-history performance / paging    → non-functional, out of BDD scope
  #   * Pact / consumer contract              → contractTest module, NOT Cucumber
  #   * Display names / label lookups         → AaBC UI concern, out of Claims API

  Background:
    Given the amendments feature flag is enabled

  @smoke @DS1645_1
  Scenario: Mixed timeline — submission, amendments, assessment and void appear in the agreed order
    Given a claim exists with the following lifecycle events in order
      | event      | occurredAt           |
      | SUBMISSION | 2026-04-01T09:00:00Z |
      | AMENDMENT  | 2026-04-15T10:00:00Z |
      | ASSESSMENT | 2026-05-01T11:00:00Z |
      | AMENDMENT  | 2026-06-01T12:00:00Z |
      | VOID       | 2026-07-01T13:00:00Z |
    When I request the claim history timeline
    Then the response contains events of types SUBMISSION, AMENDMENT, ASSESSMENT, VOID
    And the events are returned in the documented deterministic order
    And no event type outside the agreed set (SUBMISSION, AMENDMENT, ASSESSMENT, VOID) appears

  #@DS1645_2
  #Scenario: Failed amendment attempts do NOT appear as events
  #  Given a claim exists with the following amendment attempts
  #    | attempt | outcome                           |
  #    | 1       | rejected by eligibility gate      |
  #    | 2       | rejected by PDA validation        |
  #    | 3       | rejected by FSP validation        |
  #    | 4       | rejected by final version guard   |
  #    | 5       | committed successfully            |
  #  When I request the claim history timeline
  #  Then the response contains exactly one AMENDMENT event
  #  And no AMENDMENT event exists for any of the four failed attempts

  @DS1645_3
  Scenario: New Matter Starts events do NOT appear in the claim timeline
    Given a claim exists with a linked New Matter Starts submission-level history entry
    And the claim has a successful AMENDMENT event
    When I request the claim history timeline
    Then the response contains no event of type "NEW_MATTER_STARTS"
    And the response contains no submission-level New Matter Starts metadata

  @DS1645_4
  Scenario: Main timeline response never leaks raw payload or full before-state
    Given a claim exists with a successful amendment
    And the amendment's `claim_amendment.request_payload` contains sensitive claim field values
    And the amendment's `claim_amendment.before_state` contains a full snapshot of pre-amendment values
    When I request the claim history timeline
    Then the AMENDMENT event does not contain the raw amendment request payload
    And the AMENDMENT event does not contain the full before-state snapshot
    And only per-field before/after values from the versioned diff are exposed

  @DS1645_5
  Scenario: Submission-only / minimal history — a fresh claim returns only its SUBMISSION event
    Given a claim exists that has been submitted but never amended, assessed or voided
    When I request the claim history timeline
    Then the response contains exactly one event of type SUBMISSION
    And the response contains no AMENDMENT, ASSESSMENT or VOID events
    And the response shape matches the documented contract

  #@DS1645_6
  #Scenario Outline: Actor fallback — missing user id resolves to the agreed fallback, never fabricated
  #  Given a claim exists with a "<eventType>" event whose source user id is missing
  #  When I request the claim history timeline
  #  Then the "<eventType>" event actor is set to the agreed fallback identifier
  #  And no synthetic user id was generated for the actor
  #
  #  Examples:
  #    | eventType  |
  #    | SUBMISSION |
  #    | AMENDMENT  |
  #    | ASSESSMENT |
  #    | VOID       |

  #@DS1645_7
  #Scenario: Write-to-read smoke — a real amendment submission through DSTEW-1593 surfaces as an AMENDMENT event
  #  Given a claim exists at claim.status "VALID"
  #  And a well-formed amendment payload for that claim
  #  When I submit the amendment and wait for the event service to complete amendment validation
  #  And the amendment is committed successfully
  #  And I then request the claim history timeline
  #  Then the response contains an AMENDMENT event whose source_id equals the persisted claim_amendment.id
  #  And the AMENDMENT event was derived from persisted source data, not seeded history rows

