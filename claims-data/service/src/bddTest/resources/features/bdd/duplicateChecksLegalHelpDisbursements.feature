@Regression
@duplicateChecks
@disbursements
Feature: Duplicate checks - Legal Help - Disbursements (API)

  # Endpoints exercised:
  #   POST  /api/v1/bulk-submissions             — submit a bulk submission
  #   GET   /api/v1/bulk-submissions/{id}        — read persisted bulk submission (assertions)
  #   GET   /api/v1/bulk-submissions/{id}/summary — poll for terminal status (UAT mode)
  #   PATCH /api/v1/bulk-submissions/{id}        — drive terminal status in local mode
  #   GET   /api/v1/submissions/{id}/claims      — read validation errors on each claim (UAT mode)
  #   POST  /api/v1/submissions                  — local-mode seed for the void step
  #   POST  /api/v1/submissions/{id}/claims      — local-mode seed for the void step
  #   POST  /api/v1/claims/{id}/void             — void a claim (DCLHD_9 only)

  # ===========================================================================
  # 1. Two submissions more than N months apart are both accepted end-to-end.
  # ===========================================================================
  @DCLHD_1
  Scenario Outline: Two disbursement submissions more than <monthsDifference> months apart are both accepted
    Given two Legal Help "<format>" submissions for office "<office>", <monthsDifference> months apart, with the following claims
      | ucn   | feeCode1 | feeCode2 | ufn   |
      | <ucn> | ICISD    | ICISD    | <ufn> |
    When I submit the first submission and wait for the event service to complete the duplicate checks
    And I submit the second submission and wait for the event service to complete the duplicate checks
    Then the second submission is accepted

    Examples:
      | format | office | ufn        | ucn             | monthsDifference |
      | csv    | 0P322F | 020725/123 | 03021998/S/CSVA | 3                |
      | csv    | 2L849T | 020725/124 | 04021998/S/CSVA | 4                |


  # ===========================================================================
  # 2. Within-file duplicates: the submission is accepted for upload but the
  #    event-service rejects it and attaches an error to each duplicated claim.
  # ===========================================================================
  @DCLHD_2
  Scenario Outline: Two identical claims in the same submission are rejected as duplicates
    Given a Legal Help "<format>" submission with the following claims
      | ucn   | feeCode | ufn   |
      | <ucn> | ICISD   | <ufn> |
      | <ucn> | ICISD   | <ufn> |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is rejected with the following errors
      | Error Message                                          |
      | A duplicate claim was found within the same submission |
      | A duplicate claim was found within the same submission |

    Examples:
      | format | ufn        | ucn             |
      | csv    | 010825/123 | 01021998/S/CSVA |


  # ===========================================================================
  # 3. Not a duplicate when the UFN differs across two separate submissions.
  # ===========================================================================
  @DCLHD_3
  Scenario Outline: Two submissions with the same claim but different UFNs are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn   | feeCode | ufn    | office |
      | <ucn> | ICISD   | <ufn>1 | 1T102C |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Legal Help "<format>" submission with the following claims
      | ucn   | feeCode | ufn    | office |
      | <ucn> | ICISD   | <ufn>2 | 1T102C |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format | ufn       | ucn             |
      | csv    | 011025/12 | 01021998/S/CSVA |


  # ===========================================================================
  # 4. Not a duplicate when the UCN differs across two separate submissions.
  # ===========================================================================
  @DCLHD_4
  Scenario Outline: Two submissions with the same claim but different UCNs are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn    | feeCode | ufn   | office |
      | <ucn>A | ICISD   | <ufn> | 2P747T |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Legal Help "<format>" submission with the following claims
      | ucn    | feeCode | ufn   | office |
      | <ucn>B | ICISD   | <ufn> | 2P747T |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format | ufn        | ucn            |
      | csv    | 011025/123 | 01021998/S/CSV |


  # ===========================================================================
  # 5. Not a duplicate when the office code differs across two submissions.
  # ===========================================================================
  @DCLHD_5
  Scenario Outline: The same claim submitted for two different offices is accepted both times
    Given a Legal Help "<format>" submission with the following claims
      | ucn   | feeCode | ufn   | office    |
      | <ucn> | ICISD   | <ufn> | <office1> |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Legal Help "<format>" submission with the following claims
      | ucn   | feeCode | ufn   | office    |
      | <ucn> | ICISD   | <ufn> | <office2> |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format | office1 | office2 | ufn        | ucn             |
      | csv    | 1T102C  | 0P322F  | 011025/123 | 01021998/S/CSVA |


  # ===========================================================================
  # 6. Not a duplicate when the disbursement fee code differs (ICISD vs ICSSD).
  # ===========================================================================
  @DCLHD_6
  @stable
  Scenario Outline: Two submissions with different disbursement fee codes are both accepted
    Given two Legal Help "<format>" submissions for office "<office>", 1 month apart, with the following claims
      | ucn   | ufn   | feeCode1 | feeCode2 |
      | <ucn> | <ufn> | ICISD    | ICSSD    |
    When I submit the first submission and wait for the event service to complete the duplicate checks
    And I submit the second submission and wait for the event service to complete the duplicate checks
    Then the second submission is accepted

    Examples:
      | format | office | ufn         | ucn             |
      | csv    | 2P746R | 301025/§123 | 01021998/S/CSVA |


  # ===========================================================================
  # 7. Duplicate rule — the second submission is rejected when a matching
  #    claim is resubmitted within the N-month duplicate window.
  # ===========================================================================
  @DCLHD_7
  Scenario Outline: A matching claim resubmitted within <monthsDifference> months is rejected as a duplicate
    Given two Legal Help "<format>" submissions for office "<office>", <monthsDifference> months apart, with the following claims
      | ucn   | feeCode   | ufn   |
      | <ucn> | <feeCode> | <ufn> |
    When I submit the first submission and wait for the event service to complete the duplicate checks
    And I submit the second submission and wait for the event service to complete the duplicate checks
    Then the second submission is rejected with the following errors
      | Error Message  |
      | <errorMessage> |

    @smoke
    Examples:
      | format | office | ucn             | ufn        | feeCode | monthsDifference | errorMessage                                      |
      | csv    | 0P322F | 05011998/S/CSVA | 020825/523 | ICISD   | 2                | A duplicate claim was found in another submission |

    Examples:
      | format | office | ucn             | ufn        | feeCode | monthsDifference | errorMessage                                      |
      | csv    | 2L849T | 03011998/S/CSVA | 010725/323 | ICISD   | 0                | Submission already exists for Office              |
      | csv    | 0P322F | 04011998/S/CSVA | 020825/423 | ICISD   | 1                | A duplicate claim was found in another submission |


  # ===========================================================================
  # 8. Duplicate accepted when the earlier claim's CCD is on or before the
  #    duplicate-check cutoff, so the duplicate rule does not apply.
  # ===========================================================================
  @DCLHD_8
  Scenario: A duplicate claim is accepted when the earlier claim's CCD is on or before the duplicate cutoff
    Given two Legal Help "csv" submissions for office "0P322F" with the earlier claim dated on or before the duplicate cutoff, and the following claims
      | ucn             | feeCode1 | feeCode2 | ufn        |
      | 06011998/S/CSVA | ICISD    | ICISD    | 020825/623 |
    When I submit the first submission and wait for the event service to complete the duplicate checks
    Then the first submission is accepted with 1 claim
    When I submit the second submission and wait for the event service to complete the duplicate checks
    Then the second submission is accepted with 1 claim


  # ===========================================================================
  # 9. Voiding the first claim clears the duplicate lock, so the same claim
  #    can be resubmitted 2 months later.
  # ===========================================================================
  @DCLHD_9
  @void
  Scenario: Voiding the first claim allows the same disbursement claim to be resubmitted 2 months later
    Given two Legal Help "csv" submissions for office "0P322F", 2 months apart, with the following claims
      | ucn             | feeCode1 | feeCode2 | ufn        |
      | 05011998/S/CSVA | ICISD    | ICISD    | 020825/523 |
    When I submit the first submission and wait for the event service to complete the duplicate checks
    Then the first submission is accepted with 1 claim
    When I void the claim from the first submission
    And I submit the second submission and wait for the event service to complete the duplicate checks
    Then the second submission is accepted with 1 claim

