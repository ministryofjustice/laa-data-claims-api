@Regression
@amendments
@assessment
@occ
@dstew-2051
Feature: Assessment must advance claim.version so in-flight amendments are invalidated

  # Jira: DSTEW-2051 (coordinates with parent OCC story DSTEW-1658)
  # Endpoint under change: POST /api/v1/claims/{claimId}/assessments
  # Endpoint under protection: POST /api/v1/claims/{claimId}/amendments
  #
  # Bug today (empirically verified — see AmendmentVersusAssessmentOccIntegrationTest banner):
  #   AssessmentService.updateClaimAssessmentStatus() writes hasAssessment via a
  #   bulk JPQL update that bypasses Hibernate lifecycle. Two effects:
  #     1. @Version is NOT incremented on any assessment (bulk JPQL bypasses @Version).
  #     2. The write only runs for the FIRST assessment (`if (!claim.isHasAssessment())`),
  #        so 2nd+ assessments do not touch the claim row at all.
  #   Net: claim.version is unchanged before and after a successful assessment,
  #   so a stale amendment loaded before the assessment is wrongly accepted.
  #
  # Fix: mutate the already-loaded managed Claim inside createAssessment(...) so
  # Hibernate dirty-checking bumps @Version on every successful assessment,
  # inside the existing transactional boundary. Preserve hasAssessment behaviour.
  #
  # Coverage review (2026-08-11):
  #   - `AmendmentVersusAssessmentOccIntegrationTest.assessmentAfterLoad…()` is
  #     `@Disabled` and captures the primary AC. Re-enable + rely on it → covered
  #     by `@DS2051_3` at BDD level (kept because it's the user-visible outcome
  #     and is the reason for the whole ticket).
  #   - `AssessmentServiceTest` will be updated as part of the refactor (dirty
  #     check vs bulk JPQL). Unit-scope, not BDD.
  #   - `amendmentsFinalSaveGuard.feature @DS1753_*` covers amendment-vs-amendment
  #     OCC — assessment-vs-amendment is a different write path and NOT covered
  #     there. This file adds that path.
  #   - `claimsSearchEffectiveValueSort.feature @DS1947_*` (DSTEW-1947) proves
  #     effective-value collapses voids to £0.00 via the assessment rule — this
  #     file adds the OCC side of VOID assessments (`@DS2051_8`).
  #
  # OUT OF SCOPE: amendment-vs-amendment OCC → DSTEW-1753;
  #               assessed-pricing amendment rejection → DSTEW-1767;
  #               claim-history assessment/void events → DSTEW-1812;
  #               amendment endpoint orchestration → DSTEW-1771.

  Background:
    Given the assessment endpoint is available
    And the amendment endpoint is available
    And the amendments feature flag is enabled
    And claim "C" exists in status "VALID" and is amendable

  @smoke @DS2051_1
  Scenario: First successful assessment advances claim.version
    Given claim "C" has version 7
    And claim "C" has hasAssessment=false
    When I POST a valid assessment for claim "C"
    Then the response is 201 Created
    And claim "C" version is now 8
    And claim "C" hasAssessment is now true

  @DS2051_2
  Scenario: Subsequent successful assessment also advances claim.version
    # Closes root-cause #2: today the bulk update is guarded by
    # `if (!claim.isHasAssessment())` so the 2nd assessment does not touch
    # the claim row at all. After the fix, every successful assessment must
    # bump @Version whether or not hasAssessment was already true.
    Given claim "C" has version 8
    And claim "C" already has one prior assessment
    And claim "C" has hasAssessment=true
    When I POST a second valid assessment for claim "C"
    Then the response is 201 Created
    And claim "C" version is now 9
    And claim "C" hasAssessment remains true

  @DS2051_3
  Scenario: Assessment submitted after amendment-screen load invalidates the stale amendment
    # This is the primary AC — mirrors the currently @Disabled integration
    # test `AmendmentVersusAssessmentOccIntegrationTest
    # .assessmentAfterLoadInvalidatesStaleAmendmentWithConflict` which is
    # re-enabled by this story.
    Given the amendment screen loaded claim "C" at version 7
    When a concurrent assessment for claim "C" is submitted and succeeds
    And claim "C" version has advanced past 7
    And I submit a non-pricing amendment for claim "C" carrying version 7
    Then the response is 409 Conflict
    And the error code is "CLAIM_VERSION_CONFLICT"
    And the user-safe message contains "The claim has changed since it was loaded"
    And no claim_amendment row is written for claim "C"
    And claim "C" isAmended remains false
    And the rejection reason is the stale version, not the assessed-pricing gate

  @DS2051_4
  Scenario: hasAssessment lifecycle preserved across the fix
    Given claim "C" starts with hasAssessment=false and version 5
    When I POST a valid assessment for claim "C"
    Then claim "C" hasAssessment is now true
    And claim "C" version is now 6
    When I POST a second valid assessment for claim "C"
    Then claim "C" hasAssessment remains true
    And claim "C" version is now 7

  @DS2051_5
  Scenario: Assessment transactional atomicity — failed assessment does not advance version
    Given claim "C" has version 4
    And a downstream write in the assessment transaction will fail
    When I POST an assessment for claim "C"
    Then the response is not 201
    And claim "C" version is still 4
    And no assessment row was written for claim "C"
    And claim "C" hasAssessment is unchanged
    And the version advancement is bound to the same transaction as the assessment insert

  @DS2051_6
  Scenario: Amendment then assessment — each advances the version independently
    Given claim "C" has version 3
    When a valid non-pricing amendment for claim "C" carrying version 3 is committed
    Then claim "C" version is now 4
    When I POST a valid assessment for claim "C"
    Then claim "C" version is now 5

  @DS2051_7
  Scenario: Second assessment after a first assessment blocks a stale amendment loaded between them
    # Guards the exact regression the ticket calls out: today the 2nd
    # assessment path performs no claim update, so a stale amendment could
    # slip through between assessments.
    Given claim "C" has hasAssessment=true after a first assessment
    And claim "C" version is 8
    And the amendment screen loaded claim "C" at version 8
    When a second valid assessment for claim "C" is submitted and succeeds
    And claim "C" version has advanced past 8
    And I submit a non-pricing amendment for claim "C" carrying version 8
    Then the response is 409 Conflict
    And the error code is "CLAIM_VERSION_CONFLICT"
    And no claim_amendment row is written for claim "C"

  @DS2051_8
  Scenario: Void assessment also advances claim.version
    # A void is an assessment with type=VOID and monetary values zero — same
    # write path, so the same version-advance rule must apply. Cross-checks
    # DSTEW-1947 effective-value collapse: after void, subsequent amendments
    # cannot slip through on the pre-void version.
    Given claim "C" has version 10
    When I POST a VOID assessment for claim "C"
    Then the response is 201 Created
    And claim "C" version is now 11
    And a subsequent amendment carrying version 10 is rejected with 409 "CLAIM_VERSION_CONFLICT"

  @DS2051_9
  Scenario: Bulk JPQL update path is no longer used for hasAssessment
    # Assertion at the persistence-behaviour boundary — after the fix,
    # the claim update on the assessment path goes through Hibernate
    # dirty-check on the managed Claim (so @Version bumps), not the
    # `ClaimRepository.updateAssessmentStatus(...)` JPQL bulk write.
    Given claim "C" is loaded through `getValidClaimOrThrow(...)` inside the assessment transaction
    When the assessment is created
    Then hasAssessment and @Version are advanced via Hibernate dirty-checking on the managed entity
    And `ClaimRepository.updateAssessmentStatus(...)` is not invoked on the assessment path

