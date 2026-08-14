@Regression
@claimHistory
@amendments
@dstew-1813
Feature: Claim history timeline — AMENDMENT event metadata

  # Jira: DSTEW-1813 (1645-C) (parent: DSTEW-1645 → DSTEW-1999)
  # Depends on: DSTEW-1811 envelope, DSTEW-1659 claim_amendment storage.
  # Endpoint: GET /api/v1/claims/{claimId}/history
  #
  # One AMENDMENT event per successful `claim_amendment` row. Failed attempts
  # never appear (no `claim_amendment` row is written).
  #
  # Source mapping (claims.claim_amendment):
  #   event_type                          constant AMENDMENT
  #   event_timestamp                     ← created_on
  #   actor_user_id                       ← created_by_user_id (Entra UUID, as stored)
  #   source_id                           ← id (UUIDv7)
  #   metadata.requested_by_code          ← requested_by_code
  #   metadata.amendment_reason_code      ← amendment_reason_code
  #   metadata.amended_field_identifiers  ← diff.changes[].field_identifier
  #                                         WHERE change_source = 'Requested'
  #                                         (FSP/system consequences EXCLUDED
  #                                          — they surface via DSTEW-1814
  #                                          per-field detail + DSTEW-1815
  #                                          FSP metadata)
  #
  # Directly serves BC-570 history header, BC-574 codes, BC-576 latest-amendment
  # user/date/time. Codes are returned exactly as persisted — label resolution
  # (via DSTEW-1594 reference-data lookup) and display-name resolution (Entra
  # UUID → name) are AaBC-side.
  #
  # Coverage review (2026-08-11): parent `claimHistoryTimelineParent.feature`
  # asserts failed-amendment exclusion (`@DS1645_2`), mixed-timeline ordering
  # (`@DS1645_1`), no raw payload / full before-state (`@DS1645_4`), and
  # write-to-read smoke (`@DS1645_7`). DSTEW-1815 file asserts FSP metadata
  # (`pricing_recalculated`, `price_changed`, `escape_case_logged`) and
  # FSP-flavour `changes[]` entries.
  #
  # Gaps THIS file actually closes with delivered scenarios:
  #   (a) the two shipped code metadata fields (`requested_by_code` and
  #       `amendment_reason_code`) are populated on the AMENDMENT event
  #       envelope from a real persisted `claim_amendment` row;
  #   (b) multi-amendment chronology on a single claim, enabling
  #       latest-amendment derivation from the newest-first response order;
  #   (c) codes are returned exactly as persisted — no label substitution.
  #
  # NOT closed by this file (see the de-scope banner below):
  #   * `amended_field_identifiers` Requested-only filter (`@DS1813_2`) — the
  #     derivation does not ship on `main` yet, so no BDD hook exists.
  #   * `amended_field_identifiers` stored-order preservation (`@DS1813_5`) —
  #     same reason; depends on the same missing derivation.
  #
  # OUT OF SCOPE (delegated — do NOT add here):
  #   * Envelope shape                    → DSTEW-1811 (claimHistoryTimelineContract.feature)
  #   * ASSESSMENT / VOID event content   → DSTEW-1812 (claimHistoryAssessmentAndVoidEvents.feature)
  #   * Field-level before/after detail   → DSTEW-1814 (future file)
  #   * FSP / escape metadata + FSP changes[] → DSTEW-1815 (claimHistoryAmendmentEvents.feature)
  #   * Cross-cutting parent guarantees   → claimHistoryTimelineParent.feature
  #   * Requested By / Amendment Reason label lookup → DSTEW-1594 + AaBC
  #   * Display-name lookup for created_by_user_id → AaBC
  #
  # De-scoped from BDD (2026-08-13) — the delivered DSTEW-1813 commit on `main`
  # (62e0dd3e "Return 204 and skip persistence for no-op amendments") is a
  # WRITE-side no-op guard, not the read-side AMENDMENT-event metadata story the
  # 1645-C epic split implied. The AMENDMENT-event `requested_by_code` and
  # `amendment_reason_code` fields DO ship (via HISTORY_SQL in
  # `JdbcClaimHistoryRepository`), but `amended_field_identifiers`
  # (the Requested-only filtered list) is NOT built anywhere in the shipped
  # code — no SQL surface, no OpenAPI field, no controller assembly.
  #   * `@DS1813_2` — `amended_field_identifiers` FSP-exclusion filter. Cannot be
  #     exercised against today's `main`. To be re-added when the derivation
  #     ships (either under this ticket if refocused, or on a new one).
  #   * `@DS1813_5` — `amended_field_identifiers` order preservation. Same
  #     reason; delete-then-re-add.
  #   * `@DS1813_1` retains the envelope + `requested_by_code` +
  #     `amendment_reason_code` assertions (which ARE delivered) and drops the
  #     `amended_field_identifiers` assertion (which is not).

  @smoke @DS1813_1
  Scenario: Successful amendment appears as an AMENDMENT event with the delivered metadata fields
    # Note: the `amended_field_identifiers` assertion originally on this scenario has been
    # trimmed pending delivery of the Requested-only filter (see de-scope banner above).
    Given a claim exists with the following successful `claim_amendment` row
      | field                 | value                        |
      | created_on            | 2026-05-02T09:14:00Z         |
      | created_by_user_id    | entra-user-abc               |
      | requested_by_code     | PROVIDER                     |
      | amendment_reason_code | PROVIDER_ERROR               |
    And the amendment's stored diff contains a `change_source` "Requested" entry for field "client_surname"
    When I request the claim history timeline
    Then the response contains an event with the following envelope
      | envelopeField    | value                |
      | event_type       | AMENDMENT            |
      | event_timestamp  | 2026-05-02T09:14:00Z |
      | actor_id         | entra-user-abc       |
    And that event's metadata contains
      | metadataField         | value          |
      | requested_by_code     | PROVIDER       |
      | amendment_reason_code | PROVIDER_ERROR |

  @DS1813_3
  Scenario: Multi-amendment chronology — each amendment is its own event, latest is unambiguously derivable
    # AaBC BC-576 Amended banner needs the latest amendment's user/date/time.
    # Deterministic ordering + one event per row makes "latest" unambiguous.
    Given a claim exists with the following successful `claim_amendment` rows applied in order
      | created_on           | created_by_user_id | requested_by_code   |
      | 2026-04-15T10:00:00Z | user-alpha         | PROVIDER            |
      | 2026-06-01T12:00:00Z | user-beta          | CONTRACT_MANAGEMENT |
    When I request the claim history timeline
    Then the response contains exactly two AMENDMENT events
    And the LATEST AMENDMENT event has `actor_id` "user-beta" and `event_timestamp` "2026-06-01T12:00:00Z"

  @DS1813_4
  Scenario Outline: Codes are returned exactly as persisted — no label substitution in this story
    Given a claim exists with a successful `claim_amendment` row where `requested_by_code` is "<storedRequestedBy>" and `amendment_reason_code` is "<storedReason>"
    When I request the claim history timeline
    Then the AMENDMENT event metadata field "requested_by_code" is exactly "<storedRequestedBy>"
    And the AMENDMENT event metadata field "amendment_reason_code" is exactly "<storedReason>"
    And no display label has been substituted for either code

    Examples:
      | storedRequestedBy   | storedReason               |
      | PROVIDER            | PROVIDER_ERROR             |
      | CONTRACT_MANAGEMENT | INCORRECT_MEANS_ASSESSMENT |
      | ASSURANCE           | OTHER                      |


