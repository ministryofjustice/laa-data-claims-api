@Regression
@amendments
@endpoint
@dstew-1771
Feature: Amendment endpoint — end-to-end orchestration (DSTEW-1593 wiring)

  # Jira: DSTEW-1771 (parent: DSTEW-1593 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}
  #
  # Orchestration story wiring DSTEW-1593 end-to-end. This file covers ONLY the
  # cross-cutting orchestration guarantees no single child can prove:
  #   * Full-flow happy paths (non-pricing + pricing).
  #   * Step-order sequencing — an earlier failure short-circuits later steps.
  #   * No Claims-API hard response-time limit anywhere in the orchestration.
  #   * The DSTEW-1743 stub selector is unreachable on the real path.
  #
  # Assertions are driven against REAL observable state — HTTP status, the
  # MockServer PDA (/schedules) and FSP (/fee-calculation) request journals, and
  # the persisted claim / claim_amendment / calculated_fee_detail rows — rather
  # than narrative spec-guards. Monitoring (DS1771_6) has no harness-scrapeable
  # metrics subsystem, so it is proven via those same real observable proxies.
  #
  # OUT OF SCOPE (delegated to children — do NOT add here):
  #   * Individual gate error codes → their own child files
  #   * OCC 409 detail / rollback   → amendmentsFinalSaveGuard.feature
  #   * PDA / FSP call mechanics    → their own child files

  Background:
    Given the amendments feature flag is enabled

  # ============================================================================
  # Happy paths — end-to-end atomic save
  # ============================================================================

  @smoke @DS1771_1
  Scenario: End-to-end — valid non-pricing amendment saves atomically and skips PDA and FSP
    Given an orchestration claim exists with area of law "LEGAL_HELP"
    And an orchestration amendment updates only the field "client_surname" to "Jones"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted
    And no outbound PDA call was made
    And no outbound FSP call was made from the amendment harness
    And claim.is_amended is true
    And claim.version is now 1
    And exactly one claim_amendment row was inserted for this claim
    And the orchestration amendment persisted the field "client_surname" as "Jones"
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt

  @DS1771_2
  Scenario: End-to-end — valid pricing amendment calls PDA + FSP and saves all writes atomically
    Given an orchestration claim exists with area of law "LEGAL_HELP"
    And the PDA service will respond "authorised" within the amendment-path timeout
    And an orchestration pricing amendment changes the fee code within the same area of law
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted
    And exactly 1 outbound PDA call was made
    And exactly 1 outbound FSP call was made
    And claim.is_amended is true
    And claim.version is now 1
    And exactly one claim_amendment row was inserted for this claim
    And the orchestration claim now has fee code "ORCH1"
    And exactly one new calculated_fee_detail row was inserted for this amendment

  # ============================================================================
  # Step-order sequencing — earlier failures short-circuit later steps
  # ============================================================================

  @DS1771_3
  Scenario Outline: Failure at "<failingStep>" short-circuits all later orchestration steps
    Given an orchestration claim exists with area of law "LEGAL_HELP"
    And the orchestration amendment is set up to fail at step "<failingStep>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment endpoint returns HTTP status <httpStatus>
    And no outbound PDA call was made
    And no outbound FSP call was made from the amendment harness
    And no amendment state was committed
    And the claim persisted state matches the pre-amendment state
    And no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt
    And the orchestration client state is unchanged from the seed

    # Every failing step here fails BEFORE any PDA-impacting change is validated, so no outbound
    # PDA (/schedules) or FSP (/fee-calculation) call is made and nothing is committed. The
    # fee-code-lookup step is intentionally NOT in this table: a fee-code change is PDA-impacting, so
    # it necessarily exercises the PDA validator (an outbound /schedules call) before the fee-code
    # gate rejects — that short-circuit is proven in amendmentsFeeCodeLookupAndValidation.feature.
    # version contract returns 409 (CLAIM_VERSION_CONFLICT); the others aggregate as 400.
    Examples:
      | failingStep                     | httpStatus |
      | request boundary (missing body) | 400        |
      | version contract (DSTEW-1751)   | 409        |
      | metadata (DSTEW-1765)           | 400        |
      | amendability (DSTEW-1767)       | 400        |
      | duplicate (DSTEW-1769)          | 400        |

  # ============================================================================
  # No Claims-API hard response-time limit end-to-end
  # ============================================================================

  @DS1771_4
  Scenario: Slow-but-successful PDA + FSP end-to-end journey completes as HTTP 200, not a fabricated failure
    # AC7 — orchestration must not impose a Claims-API hard response-time limit.
    # Genuine dependency success (however slow, within the read budget) must complete.
    Given an orchestration claim exists with area of law "LEGAL_HELP"
    And an orchestration pricing amendment changes the fee code with slow-but-successful PDA and FSP
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted
    And the amendment processing was not aborted by any Claims-API response-time limit
    And exactly 1 outbound PDA call was made
    And exactly 1 outbound FSP call was made
    And exactly one claim_amendment row was inserted for this claim

  # ============================================================================
  # Stub removed — real endpoint on the same path
  # ============================================================================

  @DS1771_5
  Scenario: The DSTEW-1743 stub selector header is ignored — the real endpoint returns the real success
    Given an orchestration claim exists with area of law "LEGAL_HELP"
    And an orchestration amendment updates only the field "client_surname" to "Jones"
    And the amendment request carries a DSTEW-1743 stub selector header "fail:multi-error"
    When I submit the amendment with the stub selector header and wait for amendment validation
    Then the amendment is accepted
    And the response body is not the DSTEW-1743 stub error envelope
    And exactly one claim_amendment row was inserted for this claim

  # ============================================================================
  # End-to-end monitoring emitted (proven via real observable proxies)
  # ============================================================================

  @DS1771_6
  Scenario: End-to-end orchestration is observable for a successful pricing amendment
    Given an orchestration claim exists with area of law "LEGAL_HELP"
    And the PDA service will respond "authorised" within the amendment-path timeout
    And an orchestration pricing amendment changes the fee code within the same area of law
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted
    And the orchestration invoked the outbound PDA and FSP calls
    And claim.is_amended is true
    And exactly one claim_amendment row was inserted for this claim




