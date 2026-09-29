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
  #
  # ASSERTION NOTE (discriminating end-to-end design):
  #   The TARGET claim is seeded fully valid (mandatory Legal Help fee/client/case
  #   fields, mirroring AmendableClaimFixture), so the ONLY thing that can reject an
  #   amendment is the duplicate gate. This makes every assertion strict and
  #   discriminating rather than able to pass on unrelated field/contract errors:
  #     * "no duplicate validation error is raised" asserts a 2xx COMMIT (a clean or
  #       exemption-covered amendment), which cannot happen if any duplicate fired.
  #     * "rejected with error code ..." asserts the real duplicate code on a 4xx,
  #       and because the claim is otherwise valid that code is the sole reason.
  #   Independence (DS1769_3) and explicit-key (DS1769_4) scenarios seed a REAL
  #   cross-submission twin and prove which key was used by whether the duplicate
  #   fires; the DISB_ONLY exemption (DS1769_5) seeds a real colliding twin and
  #   proves the exemption by the successful commit.

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
    And an amendment updates only the field "client_surname" to "Jones"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no duplicate validation error is raised

  @DS1769_2
  Scenario: Post-amendment state duplicates another claim - rejected with the existing duplicate code
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And another claim exists with UCN "14091962/T/PERS" and UFN "150725/999" and area of law "LEGAL_HELP"
    And an amendment updates the UFN to "150725/999"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with error code "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION"
    And no duplicate amendment state was committed

  # ============================================================================
  # UCN/UFN independence - no recompute from name / DOB / Case ID
  # ============================================================================

  @DS1769_3
  Scenario Outline: Changing "<changedField>" does not move the duplicate key off the stored UCN/UFN
    # Discriminating design (addresses review): a real cross-submission twin is seeded on the
    # STORED key (UCN 14091962/T/PERS + UFN 010725/123). Because the amended field is not part of
    # the key and is never used to recompute UCN/UFN, the post-amendment key stays on the stored
    # value and the pre-existing duplicate is STILL detected. If a regression recomputed the key
    # from the changed name / DOB / case id, the collision would vanish and this rejection would
    # not fire - so the assertion proves the stored key was used.
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And another claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And an amendment updates only the field "<changedField>" to "<newValue>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with error code "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION"
    And no duplicate amendment state was committed

    Examples:
      | changedField         | newValue   |
      | client_surname       | Jones      |
      | client_forename      | Ada        |
      | client_date_of_birth | 01/01/1980 |
      | case_id              | CASE-999   |

  @DS1769_4
  Scenario Outline: Explicit "<key>" change makes the amended claim collide on the submitted value
    # Discriminating design (addresses review): a real cross-submission twin is seeded on the
    # value the amendment will submit. When the explicit key field is changed to that value the
    # post-amendment key collides with the twin and the duplicate is raised - proving the submitted
    # value (not the stored one) is used in the key.
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And another claim exists with UCN "<twinUcn>" and UFN "<twinUfn>" and area of law "LEGAL_HELP"
    And an amendment updates only the field "<key>" to "<newValue>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with error code "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION"
    And no duplicate amendment state was committed

    Examples:
      | key                  | newValue        | twinUcn         | twinUfn    |
      | unique_client_number | 07081996/S/FEEA | 07081996/S/FEEA | 010725/123 |
      | unique_file_number   | 150725/999      | 14091962/T/PERS | 150725/999 |

  # ============================================================================
  # Existing exemptions honoured; same-submission rule DOES apply (production)
  # ============================================================================

  @DS1769_5
  Scenario: Existing Legal Help disbursements-only (DISB_ONLY) exemption is honoured in the amendment flow
    # A REAL colliding cross-submission twin is seeded on the same UCN/UFN key, and the target's
    # before-state fee type is DISB_ONLY. The Legal Help disbursements-only exemption therefore
    # suppresses what would otherwise be a duplicate, and the amendment commits (204). Without the
    # exemption the seeded twin would force a rejection - so the successful commit is the
    # discriminating proof the exemption was applied.
    Given an original claim exists with area of law "LEGAL_HELP" and outcome-code exemption "DISB_ONLY"
    And another claim exists with the same UCN, UFN and area of law that would normally duplicate but qualifies for the same exemption
    And an amendment updates only the field "client_surname" to "Smith"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then no duplicate validation error is raised

  @DS1769_6
  Scenario: Same-submission duplicate rules ARE applied to amendments
    # INVERTED from the DSTEW-1999 draft: production applies the same-submission
    # duplicate rule in the amendment path (ClaimAmendmentDuplicateValidationIntegrationTest
    # #ucnAmendCreatingWithinSubmissionDuplicateIsRejected).
    Given an original claim exists with UCN "14091962/T/PERS" and UFN "010725/123" and area of law "LEGAL_HELP"
    And a sibling claim in the same submission has UCN "02021990/B/CDEF" and the same UFN and fee code
    And an amendment updates the UCN to "02021990/B/CDEF"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with error code "INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION"
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

