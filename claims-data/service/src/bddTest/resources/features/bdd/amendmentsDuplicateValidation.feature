@Regression
@amendments
@duplicateCheck
@dstew-1769
Feature: Amendment duplicate validation - reuse existing rules against post-amendment state

  # Jira: DSTEW-1769 (parent: DSTEW-1593 -> DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}  (DSTEW-1593)
  #
  # Runs AFTER post-amendment field/business validation (DSTEW-1768) and BEFORE
  # the Step 12 outcome check. Reuses the existing per-Area-of-Law duplicate
  # rules, keys, exemptions and messages verbatim - this ticket invents NO
  # amendment-specific duplicate logic.
  #
  # Key semantics (as implemented on main - see
  # ClaimAmendmentDuplicateValidationIntegrationTest and claims-validation-core
  # DuplicateClaimValidation):
  #   * The duplicate check uses the POST-AMENDMENT state.
  #   * The duplicate key is office + fee code + UFN (unique_file_number) + UCN
  #     (unique_client_number). UCN/UFN are independent stored strings - they
  #     change ONLY when explicitly submitted in the payload; changing client
  #     name / DOB / case id does NOT recompute them.
  #   * Only VALID / READY_TO_PROCESS claims in eligible submissions participate;
  #     VOID / INVALID twins are ignored, and a claim is never a duplicate of
  #     itself.
  #
  # Real outcome codes (the DSTEW-1999 draft placeholder DUPLICATE_CLAIM does not
  # exist in the catalogue):
  #   * INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION - cross-submission twin.
  #   * INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION     - same-submission twin.
  #
  # SCOPE NOTE (DSTEW-1768/1769 implementation, 2026-09):
  #   The DSTEW-1999 draft asserted (old DS1769_6) that same-submission duplicate
  #   rules are NOT applied to amendments. Production + the integration test prove
  #   the opposite: a same-submission collision created by an amendment IS rejected
  #   with INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION. DS1769_6 has therefore
  #   been INVERTED to assert the real behaviour rather than a contradicting premise.
  #   Draft error codes (DUPLICATE_CLAIM, INVALID_FIELD_VALUE) were corrected to the
  #   real shipped codes; the aggregation scenario now pairs the duplicate with the
  #   real INVALID_AMENDMENT_REASON_UNKNOWN metadata error (mirroring the integration
  #   test), since a malformed date on a typed field is rejected at the request
  #   contract before Step 12 aggregation.
  #
  # OUT OF SCOPE: changing duplicate rule definitions;
  #               inventing amendment-specific same-submission rules;
  #               physical index / query tuning;
  #               fee-code lookup + reusable field validation -> DSTEW-1768.

  Background:
    Given the amendments feature flag is enabled
    And the amendment PDA trigger will report "pda_relevant" as "true"
    And the PDA service will respond "authorised" within the amendment-path timeout

  # ============================================================================
  # Post-amendment state drives duplicate detection
  # ============================================================================

  @smoke @DS1769_1
  Scenario: Post-amendment state does not duplicate any other claim - amendment proceeds past this gate
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And no other claim exists whose duplicate key matches the post-amendment state
    And an amendment updates only the field "client_surname" to "Smith"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no duplicate validation error is raised

  @DS1769_2
  Scenario: Post-amendment state duplicates another claim - rejected with the existing duplicate code
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And another claim exists with UCN "14091962/T/PERS" and UFN "150725/999" and area of law "LEGAL_HELP"
    And an amendment updates the UFN to "150725/999"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                                        |
      | INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION |
    And no duplicate amendment state was committed

  # ============================================================================
  # UCN/UFN independence - no recompute from name / DOB / Case ID
  # ============================================================================

  @DS1769_3
  Scenario Outline: Changing "<changedField>" without explicit UCN/UFN in the payload leaves the duplicate key unchanged
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And another claim exists whose UCN/UFN would collide only if UCN/UFN were recomputed from "<changedField>"
    And an amendment updates only the field "<changedField>" to "<newValue>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no duplicate validation error is raised
    And the duplicate key used for this claim is UCN "14091962/T/PERS" and UFN "010725/123"

    Examples:
      | changedField         | newValue   |
      | client_surname       | Smith      |
      | client_forename      | Ada        |
      | client_date_of_birth | 01/01/1980 |
      | case_id              | CASE-999   |

  @DS1769_4
  Scenario Outline: Explicit "<key>" change makes the duplicate key use the submitted value
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And an amendment updates only the field "<key>" to "<newValue>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the duplicate key used for this claim uses the submitted "<key>" value "<newValue>"

    Examples:
      | key                 | newValue        |
      | unique_client_number | 07081996/S/FEEA |
      | unique_file_number   | 150725/999      |

  # ============================================================================
  # Existing exemptions honoured; same-submission rule DOES apply (production)
  # ============================================================================

  @DS1769_5
  Scenario: Existing Legal Help disbursements-only (DISB_ONLY) exemption is honoured in the amendment flow
    Given an original claim exists with area of law "LEGAL_HELP" and outcome-code exemption "DISB_ONLY"
    And another claim exists with the same UCN, UFN and area of law that would normally duplicate but qualifies for the same exemption
    And an amendment updates only the field "client_surname" to "Smith"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no duplicate validation error is raised
    And the same exemption reasoning that applies for new submissions was applied

  @DS1769_6
  Scenario: Same-submission duplicate rules ARE applied to amendments
    # INVERTED from the DSTEW-1999 draft: production applies the same-submission
    # duplicate rule in the amendment path (ClaimAmendmentDuplicateValidationIntegrationTest
    # #ucnAmendCreatingWithinSubmissionDuplicateIsRejected).
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And a sibling claim in the same submission has UCN "02021990/B/CDEF" and the same UFN and fee code
    And an amendment updates the UCN to "02021990/B/CDEF"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors
      | Error Code                                     |
      | INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION |
    And no duplicate amendment state was committed

  # ============================================================================
  # Aggregation + no persistence on rejection
  # ============================================================================

  @DS1769_7
  Scenario: Duplicate error aggregates with other Step 12 validation messages
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And another claim exists with UCN "14091962/T/PERS" and UFN "150725/999" and area of law "LEGAL_HELP"
    And an amendment updates the UFN to "150725/999"
    And the amendment supplies an unknown amendment reason code "NOT_A_REAL_REASON_CODE"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with error code "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION"
    And the amendment is rejected with error code "INVALID_AMENDMENT_REASON_UNKNOWN"
    And each error is returned in the shared Step 12 multi-message response
    And no duplicate amendment state was committed

  @DS1769_8
  Scenario: Duplicate rejection - no claim update, amendment record, calc-fee row or event is saved
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And another claim exists with UCN "14091962/T/PERS" and UFN "150725/999" and area of law "LEGAL_HELP"
    And an amendment updates the UFN to "150725/999"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with error code "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION"
    And no duplicate amendment state was committed
    And no amendment-related event was published for this attempt

