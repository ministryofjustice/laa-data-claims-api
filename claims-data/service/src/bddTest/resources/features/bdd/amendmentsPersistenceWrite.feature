@Regression
@amendments
@persistence
@dstew-1907
Feature: Amendment persistence — assemble & write the durable business record

  # Jira: DSTEW-1907 (1593-J) (parent: DSTEW-1593 → DSTEW-1999)
  #
  # This story writes, for a successful amendment:
  #   * exactly one `claim_amendment` row (UUIDv7 id, requested_by_code,
  #     amendment_reason_code, JSONB before_state / request_payload / diff,
  #     created_by_user_id, created_on)
  #   * amended values + is_amended=true — folded into DSTEW-1753's guarded UPDATE
  #   * one amendment-linked `calculated_fee_detail` row per pricing amendment
  #     (via `claim_amendment_id`; UNIQUE constraint enforces at-most-one)
  #
  # These scenarios drive REAL amendments over HTTP (PATCH the live endpoint,
  # using the DSTEW-2317 real-validation harness) and assert the actually
  # persisted rows plus the real claim-history timeline (GET /claims/{id}/history).
  # No amendment or amendment-linked fee rows are hand-seeded — the write path itself is exercised end to end.
  #
  # Gaps this file closes — API-observable write behaviours no other file covers:
  #   (a) Multi-amendment accumulation over a claim's lifetime — each successful
  #       pricing amendment adds one amendment-linked calc-fee row; non-pricing
  #       amendments add none.
  #   (b) Original-submission calc-fee rows are PRESERVED with claim_amendment_id
  #       null after amendment — never edited.
  #   (c) UUIDv7 amendment ids support chronological history ordering (observable
  #       via the timeline's source_id vs event_timestamp).
  #
  # ---------------------------------------------------------------------------
  # RECONCILIATIONS (feature intent → what is actually asserted here)
  # ---------------------------------------------------------------------------
  #   * The original draft table carried `classifier_impacts_pricing` + an
  #     `fspOutcome` column distinguishing "monetary values changed" from "same
  #     monetary values (no change)". The amendment-linked calculated_fee_detail
  #     row is inserted whenever FSP runs (i.e. whenever the amendment is
  #     pricing-impacting), REGARDLESS of whether the recalculated values
  #     differ — the "changed/same" distinction is not independently observable
  #     at this layer. These scenarios therefore drive on a single
  #     `impacts_pricing` flag; a pricing amendment always adds one calc-fee row.
  #   * The draft's `applied_at` wall-clock timestamps (Apr/Jun 2026) are not
  #     controllable — created_on is stamped server-side at commit. The real
  #     amendments here commit milliseconds apart; UUIDv7 ids and created_on
  #     still ascend in application order, which is exactly the property under
  #     test, so the specific dates are narrative only.
  #   * "the timeline's source_id / event_timestamp" for an AMENDMENT event are
  #     the persisted claim_amendment.id / created_on — asserted via the real
  #     GET /claims/{id}/history response.
  #
  # ---------------------------------------------------------------------------
  # DESCOPED — DS1907_4 (successful-amendment warning → validation_message_log.
  #            claim_amendment_id linkage) — NOT automatable as test-only work
  # ---------------------------------------------------------------------------
  #   The original draft carried a fourth scenario asserting that a non-blocking
  #   warning raised during a SUCCESSFUL amendment is written to
  #   `validation_message_log` with `claim_amendment_id` set to the amendment's
  #   id ("Open Item 4 — Abe-confirmed extension").
  #
  #   Investigation finding: the column exists (migration V39) and the
  #   ValidationMessageLog entity maps it, BUT no production code path populates
  #   it on the amendment flow. The amendment commit
  #   (ClaimAmendmentPersistenceService / ClaimAmendmentCommitService) writes NO
  #   validation_message_log row at all; the only writer is the submission path
  #   (ClaimService.saveValidationMessages), which maps (message, claim) with no
  #   amendment linkage. The claim_amendment_id column is a prepared storage
  #   delta for an UNIMPLEMENTED enhancement.
  #
  #   Making this scenario pass therefore requires PRODUCTION work (wiring
  #   amendment warnings into validation_message_log with claim_amendment_id),
  #   which is outside the test-authoring remit of this story. The scenario is
  #   held back rather than asserted against a false green. Raised as a
  #   follow-up on DSTEW-1907 (see PR / Jira comment).
  #
  # OUT OF SCOPE (delegated — do NOT add here):
  #   * Transaction boundary / rollback → DSTEW-1771 + DSTEW-1753
  #   * Version guard + version increment → DSTEW-1753
  #   * Physical schema / migration → DSTEW-1659
  #   * Diff READ rendering / presence semantics → DSTEW-1814
  #   * Reference-data FKs / lookup → DSTEW-1594
  #   * Deep persistence properties (UUIDv7 byte-format, JSONB round-trip,
  #     sparse-payload preservation, FK integrity) → @DataJpaTest layer

  Background:
    Given the amendments feature flag is enabled

  @DS1907_1
  Scenario: Multi-amendment accumulation — each successful pricing amendment adds one attributed calc-fee row; non-pricing adds none
    Given a claim exists with an original-submission calculated_fee_detail row
    When the following successful amendments are applied in order
      | order | impacts_pricing |
      | 1     | true            |
      | 2     | false           |
      | 3     | true            |
    And I request the claim history timeline for the amended claim
    Then the timeline contains 3 AMENDMENT events
    And exactly 3 claim_amendment rows exist for this claim
    And each claim_amendment row has a UUIDv7 id
    And exactly 2 amendment-linked calculated_fee_detail rows exist for this claim
    And each amendment-linked calculated_fee_detail row links to a successful pricing amendment id
    And the non-pricing amendment has no amendment-linked calculated_fee_detail row
    And at most one calculated_fee_detail row exists per claim_amendment_id

  @DS1907_2
  Scenario: Original-submission calculated_fee_detail rows are PRESERVED with claim_amendment_id null — never edited by an amendment
    Given a claim exists with an original-submission calculated_fee_detail row
    And I capture the original-submission calculated_fee_detail row as the pre-amendment baseline
    When the following successful amendments are applied in order
      | order | impacts_pricing |
      | 1     | true            |
    Then the original-submission calculated_fee_detail row is still present with claim_amendment_id still null
    And every stored value on the original-submission row matches the pre-amendment baseline exactly
    And a new amendment-linked calculated_fee_detail row exists with claim_amendment_id set to the successful amendment id
    And the original and amendment-linked rows coexist as two independent rows

  @DS1907_3
  Scenario: UUIDv7 amendment ids support chronological history ordering
    Given a claim exists with an original-submission calculated_fee_detail row
    When the following successful amendments are applied in order
      | order | impacts_pricing |
      | 1     | true            |
      | 2     | true            |
    And I request the claim history timeline for the amended claim
    Then the timeline contains 2 AMENDMENT events
    And the earlier AMENDMENT event source_id sorts before the later one when compared as a UUIDv7
    And ordering the AMENDMENT events by source_id yields the same order as ordering by event_timestamp

