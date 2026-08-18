@Regression
@claimHistory
@dstew-1811
Feature: Claim history timeline — contract skeleton & SUBMISSION event

  # Jira: DSTEW-1811 (1645-A) (parent: DSTEW-1645 → DSTEW-1999)
  # Endpoint: GET /api/v1/claims/{claimId}/history  (indicative; final names
  #                                                  agreed in this story)
  #
  # First child of the Claim History Timeline API. Ships the contract skeleton
  # + working SUBMISSION event so the envelope is proven against real data
  # before the richer event types land in DSTEW-1812/1813/1814/1815.
  #
  # Common event envelope (contract this story owns):
  #   event_type              stable machine-readable, from
  #                             {SUBMISSION, AMENDMENT, ASSESSMENT, VOID}
  #   event_timestamp
  #   actor_user_id           populated ALWAYS (fallback SYSTEM/UNAVAILABLE
  #                             when source has no user id — never omitted)
  #   source_id               submission | amendment | assessment UUID
  #   metadata                extension container — child tickets add fields
  #
  # SUBMISSION source mapping (claims.submission):
  #   event_timestamp             ← created_on
  #   actor_user_id               ← created_by_user_id  (else fallback)
  #   source_id                   ← id
  #   metadata.submission_period  ← submission_period
  #   metadata.office_account_number ← office_account_number
  #   metadata.area_of_law        ← area_of_law
  #
  # Coverage review (2026-08-11): `ClaimHistoryControllerIntegrationTest`
  # covers happy-path SUBMISSION response at controller level. Gaps this
  # file closes: (a) actor fallback when `created_by_user_id` is null;
  # (b) agreed not-found shape for unknown claim id; (c) metadata container
  # is present + shape-stable even for a submission-only history so the
  # extension point can't drift silently. All BDD-observable.
  #
  # OUT OF SCOPE (delegated to children — do NOT add here):
  #   * AMENDMENT event content        → DSTEW-1813 (future file)
  #   * ASSESSMENT / VOID event content → DSTEW-1812 (future file)
  #   * Field-level diff detail        → DSTEW-1814 (future file)
  #   * FSP / escape metadata          → claimHistoryAmendmentEvents.feature (DSTEW-1815)
  #   * Cross-cutting parent guarantees → claimHistoryTimelineParent.feature (DSTEW-1645)
  #   * Display-name lookup for user ids → AaBC UI concern
  #   * New Matter Starts events       → excluded from claim timeline

  @smoke @DS1811_1
  Scenario: SUBMISSION event returned with the common envelope + submission metadata
    Given a claim exists that has been submitted but never amended, assessed or voided
    And the claim has the following stored values
      | field              | value                |
      | id                 | claim-uuid-1         |
      | created_on         | 2026-04-22T11:26:00Z |
      | created_by_user_id | user-abc             |
    And the parent submission has the following stored values
      | field                 | value       |
      | submission_period     | APR-2026    |
      | office_account_number | 0X123Y      |
      | area_of_law           | CRIME_LOWER |
    When I request the claim history timeline
    Then the response contains exactly one event
    And that event matches the following common envelope
      | envelopeField    | value                |
      | event_type       | SUBMISSION           |
      | event_timestamp  | 2026-04-22T11:26:00Z |
      | actor_id         | user-abc             |
      | source_id        | claim-uuid-1         |
    And that event's metadata contains
      | metadataField         | value       |
      | submission_period     | APR-2026    |
      | office_account_number | 0X123Y      |
      | area_of_law           | CRIME_LOWER |

  @DS1811_2
  Scenario: Unknown claim id returns the agreed not-found response
    Given no claim exists for claim id "00000000-0000-0000-0000-000000000000"
    When I request the claim history timeline for that claim id
    Then the endpoint returns the agreed not-found response
    And the response shape matches the existing Claims API claim-not-found contract
    And no history events are returned in the body

  # De-scoped from BDD (2026-08-13) — audit trail carried in the reporting ledger:
  #   * @DS1811_3 — actor fallback when `created_by_user_id` is null. The delivered
  #     SUBMISSION event derives `actor_id` from `claim.created_by_user_id` via
  #     `COALESCE(c.created_by_user_id, 'SYSTEM')` in `HISTORY_SQL`. However every
  #     migration that has ever touched `claim.created_by_user_id` (V3 originally,
  #     no later relaxation) declares the column `TEXT NOT NULL`, so a null value
  #     cannot be inserted through any code path — Postgres blocks the write. The
  #     COALESCE-to-`SYSTEM` branch is defensive dead code end-to-end and cannot be
  #     exercised from the BDD tier. The scenario is removed until either (a) the
  #     column is relaxed, or (b) product formally closes the fallback requirement
  #     as unreachable.

  @DS1811_4
  Scenario: Metadata container is present and shape-stable — extension point for child tickets
    # Guards the contract extension point: 1812/1813/1814/1815 add fields to
    # metadata. The container must exist even on a submission-only history
    # so downstream events extend the same shape rather than reshape it.
    Given a claim exists that has been submitted but never amended, assessed or voided
    When I request the claim history timeline
    Then the SUBMISSION event contains a `metadata` object
    And the `metadata` object is present as an object type, not null and not omitted
    And no submission-only fields leak outside the `metadata` container onto the envelope

