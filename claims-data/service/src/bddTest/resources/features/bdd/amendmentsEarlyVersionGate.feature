@Regression
@amendments
@versionGuard
@dstew-1752
Feature: Amendment early version gate — pre-processing optimistic-concurrency check

  # Jira: DSTEW-1752 (parent: DSTEW-1658 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #
  # The amendment flow has TWO version guards that both emit HTTP 409 with the
  # shared CLAIM_VERSION_CONFLICT code and the shared stale-version envelope
  # (DSTEW-1754):
  #
  #   * initial_check  — ClaimVersionValidationStep (Phase 2) compares the
  #                      submitted `version` against the freshly read
  #                      beforeState version and short-circuits BEFORE the
  #                      PDA/FSP calls where the conflict can be detected first.
  #                      THIS story (DSTEW-1752).
  #   * final_save     — ClaimAmendmentCommitService.commit() (Phase 3) catches
  #                      Hibernate's OptimisticLockException at merge+flush time.
  #                      Owned by DSTEW-1753 → amendmentsFinalSaveGuard.feature.
  #
  # The early gate does NOT remove the need for the final transactional guard
  # (DSTEW-1753): a concurrent writer can still land AFTER this check passes.
  #
  # OUT OF SCOPE (owned elsewhere, deliberately NOT asserted here):
  #   * Mandatory request-field contract + missing/non-integer version 400 —
  #     DSTEW-1751 → amendmentsRequestContract.feature.
  #   * Mid-flight final transactional guard — DSTEW-1753.
  #   * PDA / FSP mechanics — DSTEW-1646 / DSTEW-1595.
  #   * Non-functional requirements (latency / external-call budget sizing).
  #
  # "save nothing" note: on an early conflict the only amendment-event source is
  # the claim_amendment row (the claim history timeline is projected from it), so
  # asserting the TOTAL absence of a claim_amendment row for the claim proves no
  # amendment event could have been emitted either.

  Background:
    Given the amendments feature flag is enabled

  # AC1 — submitted version matches current claim.version → processing continues.
  @DS1752_1
  Scenario: Submitted version matches current claim.version — the amendment flow continues past the early gate
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed non-pricing amendment
    Then the amendment is accepted
    And claim.version is now 1
    And claim.is_amended is true
    And exactly one claim_amendment row was inserted for this claim

  # AC2 — stale (behind) submitted version → 409 CLAIM_VERSION_CONFLICT.
  # Worked example from the ticket: stored version 9, user still holds version 7.
  @DS1752_2
  Scenario: Stale submitted version behind the stored version is rejected with 409 CLAIM_VERSION_CONFLICT
    Given a stored amendable claim at version 9
    And the submitted amendment carries the stale claim version 7
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"

  # AC2 — the gate is a strict inequality, not "behind only": a submitted version
  # AHEAD of the stored version is equally stale and equally rejected.
  @DS1752_3
  Scenario: Submitted version ahead of the stored version is also rejected with 409 CLAIM_VERSION_CONFLICT
    Given a stored amendable claim at version 3
    And the submitted amendment carries the stale claim version 5
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"

  # AC3 — the stale request is short-circuited BEFORE any external call, so it
  # spends no PDA/FSP budget.
  @DS1752_4
  Scenario: A stale request is detected before PDA/FSP — neither external service is called
    Given a stored amendable claim at version 9
    And the submitted amendment carries the stale claim version 7
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And no outbound PDA call was made
    And no outbound FSP call was made from the amendment harness

  # AC4 — on an early conflict, save nothing: no amendment record, no before-state
  # /diff (projected from the claim_amendment row), no claim-state update, no
  # calculated-fee rows and no event.
  @DS1752_5
  Scenario: Early version conflict persists nothing and leaves the claim untouched
    Given a stored amendable claim at version 9
    And the submitted amendment carries the stale claim version 7
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And claim.is_amended is false
    And claim.version equals 9

  # DSTEW-1754 wire contract — the early gate returns the SAME stable stale-version
  # envelope as the final-save guard: an RFC 9457 ProblemDetail whose errors array
  # carries exactly one entry with the machine-readable CLAIM_VERSION_CONFLICT code.
  @DS1752_6
  Scenario: The 409 body is the shared stale-version envelope carrying the machine-readable code
    Given a stored amendable claim at version 9
    And the submitted amendment carries the stale claim version 7
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And the response body is an RFC 9457 ProblemDetail with status 409
    And the response body's errors array carries exactly one entry with code "CLAIM_VERSION_CONFLICT"

  # DSTEW-1754 structured conflict logging — the early gate emits a WARN carrying
  # the safe diagnostic fields and conflictPoint=initial_check, and never leaks any
  # amendment payload field values.
  @DS1752_7
  Scenario: Structured WARN log — early gate emits event=CLAIM_VERSION_CONFLICT with safe fields and conflictPoint=initial_check
    Given a stored amendable claim at version 9
    And the submitted amendment carries the stale claim version 7
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And a WARN log entry from the early version gate was captured
    And the early-gate WARN log contains "event=CLAIM_VERSION_CONFLICT"
    And the early-gate WARN log contains "conflictPoint=initial_check"
    And the early-gate WARN log contains "submittedClaimVersion=7"
    And the early-gate WARN log contains "currentClaimVersion=9"
    And the early-gate WARN log contains the current claim id
    And the early-gate WARN log does not carry any amendment payload field values

