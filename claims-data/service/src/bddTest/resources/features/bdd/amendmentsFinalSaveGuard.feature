@Regression
@amendments
@versionGuard
@dstew-1753
Feature: Amendment final-save guard — commit-time OCC (@Version) protection

  # Jira: DSTEW-1753 (parent: DSTEW-1658 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #
  # The amendment flow has TWO version guards that both emit HTTP 409 with the
  # shared CLAIM_VERSION_CONFLICT code:
  #
  #   * initial_check  — ClaimVersionValidationStep (Phase 2) compares the
  #                      submitted `version` against the freshly read
  #                      beforeState version. Owned by DSTEW-1752.
  #   * final_save     — ClaimAmendmentCommitService.commit() (Phase 3) does a
  #                      merge + flush inside @Transactional(REQUIRES_NEW).
  #                      Hibernate's @Version raises OptimisticLockException
  #                      when a concurrent writer bumped the row between
  #                      Phase 1's read and this flush. THIS story.
  #
  # Simulating the mid-flight bump in BDD: the harness mocks
  # ValidationService.validateClaim(Claim, Set) which the amendment path
  # invokes AFTER Phase 1's read and BEFORE Phase 3's flush. We hook that mock
  # with a doAnswer that runs a native SQL `UPDATE claim SET version = ...`
  # before returning the happy-path result. Prepare read completes at version
  # N, external validation side-effects the row to N+1, commit's UPDATE ...
  # WHERE version = N matches 0 rows → OptimisticLockException.
  #
  # OUT OF SCOPE: initial_check gate — covered by amendmentsRequestContract
  #               (DSTEW-1751) and future DSTEW-1752 scenarios;
  #               Request-body shape — DSTEW-1754.

  Background:
    Given the amendments feature flag is enabled

  @smoke @DS1753_1
  Scenario: Happy path — no concurrent writer, commit succeeds and bumps @Version
    Given a fresh amendable claim on a legal-help submission at version 0
    And the PDA service will respond "authorised" within the amendment-path timeout
    And the FSP service will return a valid fee calculation for the amendment
    When I submit a well-formed non-pricing amendment
    Then the amendment is accepted
    And claim.version is now 1
    And claim.is_amended is true
    And exactly one claim_amendment row was inserted for this claim

  @DS1753_2
  Scenario: Concurrent writer bumps claim.version between prepare and commit → 409 CLAIM_VERSION_CONFLICT
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"

  @DS1753_3
  Scenario: Full rollback — after final-save conflict nothing amendment-side is persisted
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And no claim_amendment record was inserted for this claim by this attempt
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And claim.is_amended is false
    And claim.version equals 1

  @DS1753_3
  Scenario: Structured WARN log — final-save guard emits event=CLAIM_VERSION_CONFLICT with safe fields and conflictPoint=final_save
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And a WARN log entry from the final-save guard was captured
    And the captured WARN log contains "event=CLAIM_VERSION_CONFLICT"
    And the captured WARN log contains "conflictPoint=final_save"
    And the captured WARN log contains "submittedClaimVersion=0"
    And the captured WARN log contains the current claim id
    And the captured WARN log does not carry any amendment payload field values

  @DS1753_5
  Scenario: Wire-shape contract — 409 body is the shared amendment-validation-error envelope carrying the machine-readable code
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And the response body is an RFC 9457 ProblemDetail with status 409
    And the response body's errors array carries exactly one entry with code "CLAIM_VERSION_CONFLICT"

  @DS1753_6
  Scenario: Both guards emit the same wire contract — a repeat stale submit after a final-save 409 is rejected the same shape by the initial_check gate
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    When I submit the same amendment payload again
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And no claim_amendment record was inserted for this claim by this attempt

  @DS1753_7
  Scenario: Guard is version-based not path-based — a concurrent writer that only flips has_assessment (bumping @Version) still trips final_save
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will flip claim.has_assessment to true and advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And claim.has_assessment is true
    And claim.version equals 1
    And no claim_amendment record was inserted for this claim by this attempt


