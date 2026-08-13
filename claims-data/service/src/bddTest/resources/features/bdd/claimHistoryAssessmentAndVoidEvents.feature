@Regression
@claimHistory
@dstew-1812
Feature: Claim history timeline — ASSESSMENT & VOID events (single source, type split)

  # Jira: DSTEW-1812 (1645-B) (parent: DSTEW-1645 → DSTEW-1999)
  # Endpoint: GET /api/v1/claims/{claimId}/history  (envelope owned by DSTEW-1811)
  #
  # Both event types come from ONE source table `claims.assessment` — this is
  # a single mapping with a type split, not two integrations:
  #   event_type = VOID       where assessment_type = 'VOID'
  #   event_type = ASSESSMENT otherwise (incl. legacy null assessment_type)
  #
  # Source mapping (claims.assessment):
  #   event_timestamp             ← created_on
  #   actor_user_id               ← created_by_user_id (mandatory on table)
  #   source_id                   ← id
  #   metadata.assessment_type    ← assessment_type  (ESCAPE_CASE_ASSESSMENT,
  #                                 STAGE_DISBURSEMENT_ASSESSMENT, VOID, or
  #                                 ABSENT for legacy null rows)
  #   metadata.assessment_outcome ← assessment_outcome (nullable; VOID rows
  #                                 have none)
  #   metadata.assessment_reason  ← assessment_reason (nullable; for VOID
  #                                 rows this is the reason from the void
  #                                 request)
  #
  # Nullable rule (Required Behaviour): a null source column is ABSENT from
  # metadata, NOT defaulted to a placeholder value. Guards against invented data.
  #
  # Financial amounts (assessed/allowed) stay with the existing
  # `GET /api/v1/claims/{claimId}/assessments` endpoint — timeline metadata
  # stays light per the default agreed with AaBC.
  #
  # Coverage review (2026-08-11): `ClaimHistoryControllerIntegrationTest`
  # covers happy-path ASSESSMENT and VOID at controller level. Gaps this
  # file closes: (a) STAGE_DISBURSEMENT_ASSESSMENT sub-type; (b) legacy-null
  # assessment_type mapped to ASSESSMENT with no fabricated type value;
  # (c) null nullable columns ABSENT rather than defaulted; (d) VOID row
  # never presented as ASSESSMENT; (e) no-assessments claim.
  #
  # OUT OF SCOPE (delegated — do NOT add here):
  #   * Envelope shape           → DSTEW-1811 (claimHistoryTimelineContract.feature)
  #   * AMENDMENT event content  → DSTEW-1813 (future)
  #   * Field-level diff         → DSTEW-1814 (future)
  #   * FSP / escape metadata    → DSTEW-1815 (claimHistoryAmendmentEvents.feature)
  #   * Cross-cutting parent guarantees → claimHistoryTimelineParent.feature
  #   * Assessed/allowed financial amounts → out of timeline; kept on
  #     GET /api/v1/claims/{claimId}/assessments
  #   * Banner precedence (Amended / Assessed / Voided) → AaBC UI logic

  @smoke @DS1812_1
  Scenario: ESCAPE_CASE_ASSESSMENT row appears as an ASSESSMENT event with type / outcome / reason metadata
    Given a claim exists with the following claims.assessment row
      | field              | value                        |
      | id                 | assess-uuid-1                |
      | created_on         | 2026-05-10T14:03:00Z         |
      | created_by_user_id | user-abc                     |
      | assessment_type    | ESCAPE_CASE_ASSESSMENT       |
      | assessment_outcome | REDUCED_TO_FIXED_FEE         |
      | assessment_reason  | Escape Fee Case Assessment   |
    When I request the claim history timeline
    Then the response contains an event with the following envelope
      | envelopeField    | value                |
      | event_type       | ASSESSMENT           |
      | event_timestamp  | 2026-05-10T14:03:00Z |
      | actor_user_id    | user-abc             |
      | source_id        | assess-uuid-1        |
    And that event's metadata contains
      | metadataField      | value                      |
      | assessment_type    | ESCAPE_CASE_ASSESSMENT     |
      | assessment_outcome | REDUCED_TO_FIXED_FEE       |
      | assessment_reason  | Escape Fee Case Assessment |

  @DS1812_2
  Scenario: STAGE_DISBURSEMENT_ASSESSMENT row appears as an ASSESSMENT event, not fabricated as a different sub-type
    Given a claim exists with a claims.assessment row where assessment_type is "STAGE_DISBURSEMENT_ASSESSMENT"
    And that row's assessment_outcome is "PAID_IN_FULL"
    When I request the claim history timeline
    Then the response contains an event with event_type "ASSESSMENT"
    And that event's metadata field "assessment_type" is "STAGE_DISBURSEMENT_ASSESSMENT"
    And that event's metadata field "assessment_outcome" is "PAID_IN_FULL"

  @DS1812_3
  Scenario: VOID row appears as a VOID event carrying the void's assessment_reason, never as an ASSESSMENT
    Given a claim exists with the following claims.assessment row
      | field              | value                       |
      | id                 | assess-uuid-2               |
      | created_on         | 2026-05-12T09:41:00Z        |
      | created_by_user_id | user-xyz                    |
      | assessment_type    | VOID                        |
      | assessment_outcome |                             |
      | assessment_reason  | Voided at provider request  |
    When I request the claim history timeline
    Then the response contains an event with event_type "VOID" and source_id "assess-uuid-2"
    And the response does NOT contain an event of type "ASSESSMENT" with source_id "assess-uuid-2"
    And the VOID event metadata contains
      | metadataField     | value                      |
      | assessment_type   | VOID                       |
      | assessment_reason | Voided at provider request |
    And the VOID event metadata does NOT contain the field "assessment_outcome"

  # De-scoped from BDD (2026-08-13) — audit trail carried in the reporting ledger:
  #   * @DS1812_4 — the scenario asserts behaviour for a legacy `assessment` row whose
  #     `assessment_type` is NULL. Migration V36 (`V36__backfill_assessment_fields.sql`)
  #     backfilled every null and re-added `NOT NULL` on both `assessment_type` and
  #     `assessment_reason`, so a legacy-null row can no longer exist in the delivered schema.
  #     The corresponding read-side "legacy null → ASSESSMENT with no fabricated type" behaviour
  #     cannot be exercised end-to-end. The scenario is removed until either the constraint is
  #     relaxed or the requirement is formally closed as unreachable.
  #   * @DS1812_5, `assessment_reason` example — same root cause. `assessment_reason` is now
  #     `NOT NULL` in the DB (V36), so the "null column ABSENT from metadata" property cannot
  #     be exercised for that column. The `assessment_outcome` example remains in scope
  #     (that column stayed nullable per V34).

  @DS1812_5
  Scenario Outline: Null "<nullableColumn>" is ABSENT from metadata, not defaulted to a placeholder
    Given a claim exists with a claims.assessment row where "<nullableColumn>" is null
    And the other assessment columns are populated
    When I request the claim history timeline
    Then the corresponding ASSESSMENT event's metadata does NOT contain the field "<nullableColumn>"
    And no placeholder value has been substituted for "<nullableColumn>"

    Examples:
      | nullableColumn      |
      | assessment_outcome  |

  @DS1812_6
  Scenario: ASSESSMENT and VOID events interleave with SUBMISSION in chronological position by created_on
    Given a claim exists with the following lifecycle events in stored `created_on` order
      | event      | source_id     | created_on           | metadata_type          |
      | SUBMISSION | sub-uuid-1    | 2026-04-22T11:26:00Z |                        |
      | ASSESSMENT | assess-uuid-3 | 2026-05-10T14:03:00Z | ESCAPE_CASE_ASSESSMENT |
      | VOID       | assess-uuid-4 | 2026-05-12T09:41:00Z | VOID                   |
    When I request the claim history timeline
    Then the timeline contains events in the documented default order
      | event      | source_id     |
      | SUBMISSION | sub-uuid-1    |
      | ASSESSMENT | assess-uuid-3 |
      | VOID       | assess-uuid-4 |

  @DS1812_7
  Scenario: A claim with no assessment rows returns a timeline without ASSESSMENT or VOID events
    Given a claim exists that has been submitted but has no claims.assessment rows
    When I request the claim history timeline
    Then the response contains no event of type "ASSESSMENT"
    And the response contains no event of type "VOID"
    And no error is returned
    And no empty ASSESSMENT or VOID stub event is returned

