@Regression
@claimHistory
@amendments
@dstew-1815
Feature: Claim history timeline — AMENDMENT event FSP repricing & escape-case metadata

  # Jira: DSTEW-1815 (parent: DSTEW-1645 → DSTEW-1999)
  # Deps: DSTEW-1813 (surrounding AMENDMENT event metadata),
  #       DSTEW-1814 (field-level diff exposure),
  #       DSTEW-1659 (schema), DSTEW-1762 (FSP outcome handoff),
  #       DSTEW-1811 (final contract shape — field names may still change).
  # Endpoint: GET /api/v1/claims/{claimId}/history
  #
  # Three AMENDMENT-event metadata fields under test:
  #   * pricing_recalculated — an amendment-linked calculated_fee_detail row
  #                            exists (proves FSP produced an outcome for THIS
  #                            amendment, incl. same-value repricing).
  #   * price_changed        — value of calculated_fee_detail.is_price_changed
  #                            on the amendment-linked row. Meaningful only
  #                            when pricing_recalculated=true.
  #   * escape_case_logged   — an amendment-linked transition INTO escape
  #                            (e.g. change_source='FSP' entry on
  #                            calculated_fee_detail.escape_case_flag → true).
  #                            NEVER derived from current/latest claim escape
  #                            state.
  #
  # Coverage review (2026-08-05): SQL implementation exists in
  # `JdbcClaimHistoryRepository` (lines 64-136) but NO existing test invokes
  # the history endpoint for AMENDMENT events. All 9 ACs are new BDD coverage.
  #
  # OUT OF SCOPE: when FSP is called (DSTEW-1595/1762), is_price_changed
  # population (DSTEW-1595), current-value display, wording / formatting,
  # escape-case business rules.

  Background:
    Given the amendments feature flag is enabled

  # ============================================================================
  # AC1 / AC2 — Pricing amendment with FSP-linked calc-fee row
  # ============================================================================

  @smoke @DS1815_1
  Scenario: Pricing amendment where FSP changed monetary values — pricing_recalculated=true, price_changed=true
    Given a claim exists with a successful amendment
    And the amendment has an amendment-linked calculated_fee_detail row with is_price_changed set to true
    When I request the claim history timeline
    Then the response contains an AMENDMENT event for that amendment
    And the AMENDMENT event metadata field "pricing_recalculated" is true
    And the AMENDMENT event metadata field "price_changed" is true

  @DS1815_2
  Scenario: Pricing amendment where FSP returned the same monetary values — pricing_recalculated=true, price_changed=false
    Given a claim exists with a successful amendment
    And the amendment has an amendment-linked calculated_fee_detail row with is_price_changed set to false
    When I request the claim history timeline
    Then the response contains an AMENDMENT event for that amendment
    And the AMENDMENT event metadata field "pricing_recalculated" is true
    And the AMENDMENT event metadata field "price_changed" is false

  # ============================================================================
  # AC3 — Field-level FSP consequences remain identifiable
  # ============================================================================

  @DS1815_3
  Scenario: FSP-driven field changes appear in the changes array with change_source "FSP"
    Given a claim exists with a successful amendment
    And the amendment diff contains a change_source "FSP" entry for field "fee.totalAmount" from "100.00" to "125.00"
    When I request the claim history timeline
    Then the AMENDMENT event metadata "changes" array contains an entry with field_identifier "fee.totalAmount" and change_source "FSP"

  # ============================================================================
  # AC4 — Non-pricing amendment must not fabricate pricing metadata
  # ============================================================================

  @DS1815_4
  Scenario: Non-pricing amendment with no amendment-linked calc-fee row — no FSP metadata fabricated
    Given a claim exists with a successful amendment
    And the amendment has no amendment-linked calculated_fee_detail row
    And the claim's latest calculated_fee_detail row belongs to an earlier submission or amendment
    When I request the claim history timeline
    Then the response contains an AMENDMENT event for that amendment
    And the AMENDMENT event metadata field "pricing_recalculated" is absent or false
    And the AMENDMENT event metadata field "price_changed" is absent or false
    And the AMENDMENT event metadata is not derived from the claim's latest calculated_fee_detail row

  # ============================================================================
  # AC5 — Amendment caused escape transition
  # ============================================================================

  @DS1815_5
  Scenario: Amendment produced an amendment-linked escape transition — escape_case_logged=true
    Given a claim exists that was NOT flagged as an escape case before the amendment
    And a successful amendment produced an amendment-linked transition of calculated_fee_detail.escape_case_flag from false to true
    When I request the claim history timeline
    Then the AMENDMENT event metadata field "escape_case_logged" is true

  # ============================================================================
  # AC6 — Already-escaped claim must not be attributed to a later amendment
  # ============================================================================

  @DS1815_6
  Scenario: A later amendment on an already-escaped claim does not claim to have caused escape
    Given a claim exists that was ALREADY flagged as an escape case before this amendment
    And a later successful amendment has an amendment-linked calculated_fee_detail row
    And the later amendment did NOT produce a new amendment-linked escape transition
    When I request the claim history timeline
    Then the AMENDMENT event metadata field "escape_case_logged" for the later amendment is absent or false
    And the AMENDMENT event metadata for the later amendment is not derived from the claim's current escape state

  # ============================================================================
  # AC7 — Non-pricing amendment on an already-escaped claim
  # ============================================================================

  @DS1815_7
  Scenario: Non-pricing amendment on an already-escaped claim — neither pricing nor escape metadata is fabricated
    Given a claim exists that was ALREADY flagged as an escape case
    And a successful non-pricing amendment is applied
    And the amendment has no amendment-linked calculated_fee_detail row
    When I request the claim history timeline
    Then the AMENDMENT event metadata field "pricing_recalculated" is absent or false
    And the AMENDMENT event metadata field "price_changed" is absent or false
    And the AMENDMENT event metadata field "escape_case_logged" is absent or false

  # ============================================================================
  # AC8 — Failed / rejected amendments produce no AMENDMENT event
  # ============================================================================
  #
  # DE-SCOPED: the outline previously asserted four distinct write-side
  # distinct write-side failure paths (FSP validation reject, FSP technical
  # failure, post-FSP optimistic version guard, post-FSP persistence failure).
  # Driving any of those from the BDD tier requires the write-side amendment
  # harness (WireMock PDA/FSP stubs + event-service test hook) which is not
  # yet in this project — deferred to DSTEW-1770. The scenario below is the
  # honest reduction: it asserts the single guarantee this file CAN exercise
  # at the read-model tier — a claim with no persisted `claim_amendment` row
  # has no AMENDMENT event, regardless of the write-side reason. The four
  # specific failure paths are tracked in the audit ledger for the follow-up
  # ticket once the harness lands.

  @DS1815_8
  Scenario: A failed amendment attempt leaves no persisted row and produces no AMENDMENT event
    Given a claim exists
    And no `claim_amendment` row has been persisted for that claim
    When I request the claim history timeline
    Then the response contains no AMENDMENT event
    And the response contains no FSP repricing or escape metadata

  # ============================================================================
  # AC9 — Contract shape (absence + false semantics; final names owned by 1811)
  # ============================================================================

  @DS1815_9
  Scenario Outline: Contract — <case> maps to the documented absence / value rule
    Given a claim exists with the described amendment scenario "<case>"
    When I request the claim history timeline
    Then the AMENDMENT event metadata for that amendment satisfies "<expectedContract>"

    Examples:
      | case                                        | expectedContract                                                                              |
      | pricing amendment, FSP changed values       | pricing_recalculated=true; price_changed=true                                                 |
      | pricing amendment, FSP same-value repricing | pricing_recalculated=true; price_changed=false                                                |
      | non-pricing amendment                       | pricing_recalculated absent or false; price_changed absent or false                           |
      | amendment caused escape                     | escape_case_logged=true                                                                       |
      | later amendment on already-escaped claim    | escape_case_logged absent or false                                                            |
      | failed amendment                            | no AMENDMENT event, no FSP or escape metadata                                                 |

  # ============================================================================
  # Edge — multiple successful amendments must not cross-contaminate
  # ============================================================================

  @DS1815_10
  Scenario: Multiple successful amendments each carry their own pricing / escape metadata
    Given a claim exists with two successful amendments applied in order A then B
    And amendment A has an amendment-linked calculated_fee_detail row with is_price_changed set to false and no escape transition
    And amendment B has an amendment-linked calculated_fee_detail row with is_price_changed set to true and produced an escape transition
    When I request the claim history timeline
    Then the AMENDMENT event for amendment A has "pricing_recalculated" true, "price_changed" false, and "escape_case_logged" absent or false
    And the AMENDMENT event for amendment B has "pricing_recalculated" true, "price_changed" true, and "escape_case_logged" true


