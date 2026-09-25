@Regression
@amendments
@feeCodeLookup
@dstew-1768
Feature: Amendment fee-code lookup & fee-code Area-of-Law gate

  # Jira: DSTEW-1768 (parent: DSTEW-1593 -> DSTEW-1999)
  # Endpoint: POST /api/v1/claims/{claimId}/amendments  (DSTEW-1593)
  # Dependency: LFSP-418 -- GET Fee Code Details API (adds Area of Law).
  #
  # Concern owned by this file: fee-code detail retrieval and the fee-code
  # Area-of-Law gate that runs when an amendment changes the fee code. The Area
  # of Law of the (new) fee code is resolved by the reusable claims-validation
  # package (which calls GET /api/v2/fee-details/{feeCode} on the Fee Scheme
  # Platform) and surfaced on the validation result. The amendment external
  # validation step then rejects a fee-code change whose resolved Area of Law
  # differs from the claim's.
  #
  # Gate / outcome codes (as implemented on main -- see
  # ClaimAmendmentFeeCodeAreaOfLawIntegrationTest and claims-validation-core
  # ClaimValidationError):
  #   * INVALID_FEE_CODE_AREA_OF_LAW_CHANGE
  #       -- fee code change moves to a different Area of Law (terminal reject).
  #   * TECHNICAL_ERROR_FEE_SCHEME_API
  #       -- controlled no-save terminal when the fee-details lookup is genuinely
  #          unavailable / not-found / times out. (The DSTEW-1999 draft used a
  #          placeholder TECHNICAL_ERROR_FEE_CODE_DETAILS_LOOKUP "confirm during
  #          implementation"; the real shipped code is TECHNICAL_ERROR_FEE_SCHEME_API.)
  #   * MISSING_MANDATORY_FIELD / INVALID_UNIQUE_FILE_NUMBER_FORMAT
  #       -- reusable-validation codes surfaced against the POST-AMENDMENT state.
  #
  # SCOPE NOTE (DSTEW-1768 implementation, 2026-09):
  #   The original DSTEW-1999 draft carried four further scenarios (DS1768_4..7)
  #   asserting that a fee-code change can make a previously-optional field become
  #   mandatory, or impose a field regex pattern, driven per fee code by the Fee
  #   Code Details lookup. On main the fee-details response drives ONLY Area of Law
  #   (feeType/description/categoryOfLawCodes aside) -- it does NOT carry per-fee-code
  #   mandatory-field or pattern rules (that capability is LFSP-418, explicitly OUT
  #   OF SCOPE below and not yet built). Those four scenarios were removed rather
  #   than silently faked; the survivors are renumbered DS1768_1..6. When LFSP-418
  #   lands, the mandatory/pattern-enrichment scenarios can be restored.
  #
  # OUT OF SCOPE: FSP repricing -> DSTEW-1595 workstream;
  #               PDA call mechanics -> DSTEW-1772 / DSTEW-1773;
  #               Duplicate validation -> DSTEW-1769;
  #               Fee Code Details per-fee-code field rules -> LFSP-418.

  Background:
    Given the amendments feature flag is enabled
    And the Fee Code Details lookup is available

  # ============================================================================
  # Fee-code Area-of-Law gate (INVALID_FEE_CODE_AREA_OF_LAW_CHANGE)
  # ============================================================================

  @smoke @DS1768_1
  Scenario: Fee-code change within the same Area of Law proceeds past this gate
    Given an original claim exists with feeCode "CRIME-A" and area of law "CRIME_LOWER"
    And the Fee Code Details lookup returns area of law "CRIME_LOWER" for feeCode "CRIME-B"
    And the amendment changes the fee code to "CRIME-B"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted

  @DS1768_2
  Scenario: Fee-code change to a different Area of Law is rejected with INVALID_FEE_CODE_AREA_OF_LAW_CHANGE
    Given an original claim exists with feeCode "CRIME-A" and area of law "CRIME_LOWER"
    And the Fee Code Details lookup returns area of law "LEGAL_HELP" for feeCode "LH-1"
    And the amendment changes the fee code to "LH-1"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                          |
      | INVALID_FEE_CODE_AREA_OF_LAW_CHANGE |
    And no outbound FSP call was made from the amendment harness
    And no fee-code amendment state was committed

  @DS1768_3
  Scenario: Unknown fee code (no fee-details entry) surfaces the controlled fee-scheme technical error
    # The DSTEW-1999 draft expected a validation-catalogue INVALID_FEE_CODE here; on
    # main a fee-details 404 (fee code not found) cannot resolve an Area of Law and is
    # mapped by claims-validation-core to the controlled no-save TECHNICAL_ERROR_FEE_SCHEME_API
    # (proven by ClaimAmendmentFeeCodeAreaOfLawIntegrationTest#feeSchemeApiNotFound...).
    Given an original claim exists with feeCode "CRIME-A" and area of law "CRIME_LOWER"
    And the Fee Code Details lookup has no entry for feeCode "NOT-A-FEE"
    And the amendment changes the fee code to "NOT-A-FEE"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                     |
      | TECHNICAL_ERROR_FEE_SCHEME_API |
    And no fee-code amendment state was committed

  # ============================================================================
  # Reusable validation runs against the POST-AMENDMENT state and aggregates
  # ============================================================================

  @DS1768_4
  Scenario: Multiple reusable-validation failures aggregate in the shared Step 12 response
    # Same Area of Law so the terminal AoL gate does NOT short-circuit; the reusable
    # validators then run against the post-amendment state and their ERROR issues
    # aggregate into the single Step 12 multi-message response.
    Given an original claim exists with feeCode "CRIME-A" and area of law "CRIME_LOWER"
    And the Fee Code Details lookup returns area of law "CRIME_LOWER" for feeCode "CRIME-B"
    And the amendment applies the following fee-code and field changes
      | field              | newValue  |
      | fee_code           | CRIME-B   |
      | unique_file_number | not-a-ufn |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors in any order
      | Error Code                        |
      | INVALID_UNIQUE_FILE_NUMBER_FORMAT |
      | MISSING_MANDATORY_FIELD           |
    And each error is returned in the shared Step 12 multi-message response
    And no fee-code amendment state was committed

  # ============================================================================
  # Fee Code Details lookup mechanics -- no hard limit + controlled failure
  # ============================================================================

  @DS1768_5
  Scenario: Slow-but-successful Fee Code Details lookup completes without a Claims-API hard response-time limit
    # AC2 -- the amendment path has no Claims-API-level hard response-time limit; a
    # lookup that returns success within the fee-scheme read budget must continue
    # validation, not fabricate a technical error. (Feature narrative "8 seconds" is
    # scaled to a sub-timeout delay in the harness, per the PDA-mechanics convention.)
    Given an original claim exists with feeCode "CRIME-A" and area of law "CRIME_LOWER"
    And the Fee Code Details lookup will respond successfully after 8 seconds for feeCode "CRIME-B"
    And the amendment changes the fee code to "CRIME-B"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted
    And Fee Code Details monitoring records outcome "success" with a non-zero call duration

  @DS1768_6
  Scenario Outline: Fee Code Details lookup <failureKind> returns a controlled terminal technical failure
    Given an original claim exists with feeCode "CRIME-A" and area of law "CRIME_LOWER"
    And the Fee Code Details lookup will "<failureBehaviour>"
    And the amendment changes the fee code to "CRIME-B"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the endpoint responds with a controlled terminal failure "TECHNICAL_ERROR_FEE_SCHEME_API"
    And no fee-code amendment state was committed
    And Fee Code Details monitoring records outcome "<expectedOutcome>" with a non-zero call duration

    Examples:
      | failureKind             | failureBehaviour              | expectedOutcome   |
      | HTTP 5xx                | respond with HTTP 503         | technical_failure |
      | connection failure      | reject the connection         | technical_failure |
      | genuine service timeout | not respond before 30 seconds | timeout           |


