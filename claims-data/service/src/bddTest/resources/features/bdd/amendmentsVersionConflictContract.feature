@Regression
@amendments
@versionGuard
@dstew-1754
Feature: Amendment stale-version conflict — shared CLAIM_VERSION_CONFLICT envelope & structured logging

  # Jira: DSTEW-1754 (parent: DSTEW-1658 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #
  # DSTEW-1754 owns the SHARED stale-version conflict CONTRACT that both version
  # guards reuse. The sibling stories own *detecting* a stale request; this story
  # owns the *response + logging* produced once a conflict is detected, and proves
  # it is identical no matter which guard fired:
  #
  #   * initial_check — ClaimVersionValidationStep (Phase 2) short-circuits when the
  #                     submitted `version` differs from the freshly read
  #                     beforeState version. Detection owned by DSTEW-1752.
  #   * final_save    — ClaimAmendmentCommitService.commit() (Phase 3) catches
  #                     Hibernate's OptimisticLockException at merge+flush time.
  #                     Detection owned by DSTEW-1753.
  #
  # The 1658 parent feature explicitly delegates "CLAIM_VERSION_CONFLICT envelope
  # shape + structured logger" to THIS story, so the coverage here is the shared
  # contract both guards must honour:
  #   * a 409 Conflict carrying the stable machine-readable code
  #     CLAIM_VERSION_CONFLICT inside the amendment-validation `errors` envelope;
  #   * the exact user-safe message "The claim has changed since it was loaded.
  #     Review the latest claim details and try again." (ClaimAmendmentValidationCode
  #     message template — carried on the error entry, so it is identical whichever
  #     guard raised it);
  #   * a structured WARN log for support/investigation carrying only safe fields
  #     (event name, claim id, submitted/current claim versions where available,
  #     conflict point) and NEVER amendment payload values or financial details.
  #
  # "where available" nuance (Required Behaviour): the early gate has both the
  # submitted and the current stored version in hand, so it logs both. The final
  # guard's row was advanced by a concurrent writer, so the current version is not
  # available there — it logs the submitted (prepare-time) version only.
  #
  # Reconciliation against the shipped implementation (verified 2026-10-01 against
  # ClaimAmendmentValidationCode, ClaimAmendmentValidationError, ClaimVersionValidationStep,
  # ClaimAmendmentCommitService and DataClaimsExceptionHandler):
  #   * The user-safe message lives on the error entry (errors[0].message) for BOTH
  #     paths — ClaimAmendmentValidationError.of(CLAIM_VERSION_CONFLICT) formats the
  #     code's message template. Asserting errors[0].message is therefore the
  #     guard-independent equivalence anchor.
  #   * Successful amendments return 204; a conflict returns 409 with the errors
  #     envelope. The envelope is an RFC 9457 ProblemDetail with an `errors` array.
  #
  # OUT OF SCOPE (owned elsewhere, deliberately NOT re-proven here):
  #   * Deciding a request is stale — DSTEW-1752 (early) / DSTEW-1753 (final).
  #   * Request-field contract / missing-version 400 — DSTEW-1751.
  #   * Consumer provider-contract (pact) coverage of the stale-version response is
  #     exercised by the provider pact suite (DataClaimsApiProviderTests), not BDD.
  #   * Non-functional requirements (latency / external-call budget sizing).

  Background:
    Given the amendments feature flag is enabled

  # AC1 — an early (initial_check) stale conflict returns the shared envelope with
  # the stable code and the exact user-safe message.
  @DS1754_1
  Scenario: Early-gate stale conflict returns 409 with the shared CLAIM_VERSION_CONFLICT envelope and user-safe message
    Given a fresh amendable claim on a legal-help submission at version 9
    And the amendment is submitted carrying claim version 7
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And the response body is an RFC 9457 ProblemDetail with status 409
    And the response body's errors array carries exactly one entry with code "CLAIM_VERSION_CONFLICT"
    And the stale-version error message is "The claim has changed since it was loaded. Review the latest claim details and try again."

  # AC2 — a final-guard (final_save) stale conflict returns the SAME code, the SAME
  # envelope shape and the SAME user-safe message. Identical literals to DS1754_1 ⇒
  # the response mapping is shared across both conflict points.
  @DS1754_2
  Scenario: Final-guard stale conflict returns the same 409 envelope and the same user-safe message
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And the response body is an RFC 9457 ProblemDetail with status 409
    And the response body's errors array carries exactly one entry with code "CLAIM_VERSION_CONFLICT"
    And the stale-version error message is "The claim has changed since it was loaded. Review the latest claim details and try again."

  # AC3 + AC4 — the early gate logs a structured WARN with the safe fields: event
  # name, claim id, both versions (available here) and conflictPoint=initial_check,
  # and never the amendment payload values or financial details.
  @DS1754_3
  Scenario: Early-gate conflict is logged as a WARN with safe fields and conflictPoint=initial_check
    Given a fresh amendable claim on a legal-help submission at version 9
    And the amendment is submitted carrying claim version 7
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And a stale-version conflict WARN was logged at the initial_check point
    And the stale-version conflict log contains "event=CLAIM_VERSION_CONFLICT"
    And the stale-version conflict log contains "conflictPoint=initial_check"
    And the stale-version conflict log contains "submittedClaimVersion=7"
    And the stale-version conflict log contains the current stored claim version
    And the stale-version conflict log contains the current claim id
    And the stale-version conflict log carries no amendment payload or financial values

  # AC3 + AC4 — the final guard logs the same event name + claim id + conflict point
  # (final_save) and the submitted version. The current version is NOT available at
  # the final guard (a concurrent writer advanced the row), so it is omitted — the
  # "where available" qualifier in the required behaviour.
  @DS1754_4
  Scenario: Final-guard conflict is logged as a WARN with safe fields and conflictPoint=final_save
    Given a fresh amendable claim on a legal-help submission at version 0
    And a concurrent writer will advance claim.version by 1 during external validation
    When I submit a well-formed non-pricing amendment
    Then the amendment is rejected with HTTP 409 and amendment error code "CLAIM_VERSION_CONFLICT"
    And a stale-version conflict WARN was logged at the final_save point
    And the stale-version conflict log contains "event=CLAIM_VERSION_CONFLICT"
    And the stale-version conflict log contains "conflictPoint=final_save"
    And the stale-version conflict log contains "submittedClaimVersion=0"
    And the stale-version conflict log contains the current claim id
    And the stale-version conflict log carries no amendment payload or financial values

