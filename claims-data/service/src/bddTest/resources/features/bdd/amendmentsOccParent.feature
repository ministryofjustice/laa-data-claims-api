@Regression
@amendments
@versionGuard
@dstew-1658
Feature: Amendment OCC — parent-level end-to-end contract sweep

  # Jira: DSTEW-1658 (parent: DSTEW-1593 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #
  # Parent orchestration story for optimistic concurrency control (OCC) on
  # amendment submit. Split across 4 children:
  #   * DSTEW-1751 → request contract (missing / non-integer version)
  #                 → amendmentsRequestContract.feature
  #   * DSTEW-1752 → early stale-version gate                → amendmentsRequestContract scope
  #   * DSTEW-1753 → final guarded save + version increment
  #                 → amendmentsFinalSaveGuard.feature
  #   * DSTEW-1754 → shared CLAIM_VERSION_CONFLICT envelope + structured
  #                  warning logger (unit-tested in ClaimAmendmentCommitServiceTest).
  #
  # Following the parent-file precedent (DSTEW-1595 / 1646 / 1645 / 1658 / 1770),
  # this file covers ONLY cross-cutting scenarios that no single child can prove
  # on its own:
  #   * The 4-outcome HTTP contract sweep (missing/early-stale/final-stale/valid)
  #     showing the children compose correctly end-to-end.
  #   * The "no persistence at ANY layer" conflict guarantee across both the
  #     early gate and the final guard.
  #   * The early-stale short-circuit preserving PDA/FSP external-call budget.
  #
  # ---------------------------------------------------------------------------
  # Reconciliation of the stale draft against the shipped implementation
  # (verified 2026-10-01 against ClaimController, ClaimVersionValidationStep and
  # ClaimAmendmentCommitService):
  #   * Endpoint/field: the draft's `POST /claims/{id}/amendments` + `claim_version`
  #     is the shipped `PATCH /api/v1/submissions/{submissionId}/claims/{claimId}`
  #     carrying the `version` field. The feature keeps the readable `claim_version`
  #     wording in the payload-shape labels; the step composer maps it to `version`.
  #   * A successful amendment returns HTTP 204 (ResponseEntity.noContent), NOT the
  #     draft's 200 — the valid row asserts 204.
  #   * The early gate (ClaimVersionValidationStep) rejects a submitted version that
  #     is EITHER behind OR ahead of stored (full Long inequality) → 409
  #     CLAIM_VERSION_CONFLICT, logging conflictPoint=initial_check.
  #   * The final guard (ClaimAmendmentCommitService merge+flush) maps Hibernate's
  #     OptimisticLockException → 409, logging conflictPoint=final_save. The
  #     concurrent writer legitimately advances the stored version to 8, so the
  #     final-guard no-write row asserts claim.is_amended=false + the real final
  #     version (8), NOT "state matches pre-amendment" (which would be false by
  #     design once the concurrent writer has advanced the row).
  #   * "no amendment diff was persisted" is folded into "no claim_amendment record
  #     was inserted" — the diff is stored ON the claim_amendment row, so zero rows
  #     already proves no diff was persisted.
  #
  # OUT OF SCOPE (delegated to children — do NOT add here):
  #   * Request-body contract detail (Outline of malformed values) → DSTEW-1751
  #   * Early stale-version gate detail                            → DSTEW-1752
  #   * Final OCC guard atomicity + version increment              → DSTEW-1753
  #   * CLAIM_VERSION_CONFLICT envelope shape + structured logger  → DSTEW-1754

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"
    And the PDA service will respond "authorised" within the amendment-path timeout

  @smoke @DS1658_1
  Scenario Outline: End-to-end OCC contract sweep — <case> returns the documented outcome
    Given an original claim exists at stored version <storedVersion>
    And an amendment payload built as "<payloadShape>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the OCC endpoint response status is <httpStatus>
    And the response body indicates "<errorCode>"

    Examples:
      | case                                              | storedVersion | payloadShape                                                      | httpStatus | errorCode              |
      | missing claim_version                             | 7             | omit claim_version                                                | 400        | request-validation     |
      | early gate — submitted version behind stored      | 7             | claim_version=6                                                   | 409        | CLAIM_VERSION_CONFLICT |
      | early gate — submitted version ahead of stored    | 7             | claim_version=8                                                   | 409        | CLAIM_VERSION_CONFLICT |
      | final guard — another commit lands after the gate | 7             | claim_version=7 with concurrent commit advancing to 8 before save | 409        | CLAIM_VERSION_CONFLICT |
      | valid — matches stored version                    | 7             | claim_version=7                                                   | 204        | none                   |

  @DS1658_2
  Scenario Outline: <conflictGate> version conflict writes nothing at any storage layer
    # "no amendment diff was persisted" is proven by "no claim_amendment record" — the
    # diff lives on that row. The early gate leaves the version at 7; the final guard
    # row is advanced to 8 by the concurrent writer (not by our amendment), so the
    # no-write guarantee is asserted as is_amended=false + the real final version.
    Given an original claim exists at stored version <storedVersion>
    And an amendment payload built as "<payloadShape>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the OCC endpoint response status is 409
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And claim.is_amended is false
    And no amendment-related event was published for this attempt
    And claim.version is now <expectedFinalVersion>

    Examples:
      | conflictGate | storedVersion | payloadShape                                                      | expectedFinalVersion |
      | early gate   | 7             | claim_version=6                                                   | 7                    |
      | final guard  | 7             | claim_version=7 with concurrent commit advancing to 8 before save | 8                    |

  @DS1658_3
  Scenario: Early stale-version conflict short-circuits before any PDA or FSP call
    # The cross-ticket budget-preservation guarantee: an early stale conflict must
    # NOT spend PDA/FSP external-call budget, and its structured log must carry only
    # safe fields (no amendment payload values, no financial values).
    Given an original claim exists at stored version 7
    And an amendment payload built as "claim_version=6"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the OCC endpoint response status is 409
    And the response body indicates "CLAIM_VERSION_CONFLICT"
    And no outbound PDA call was made
    And no outbound FSP call was made from the amendment harness
    And the structured conflict log entry contains "conflictPoint=initial_check"
    And the structured conflict log entry does not contain any amendment payload values
    And the structured conflict log entry does not contain any financial values

