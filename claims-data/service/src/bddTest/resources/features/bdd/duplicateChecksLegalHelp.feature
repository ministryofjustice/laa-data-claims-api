@Regression
@duplicateChecks
Feature: Duplicate checks - Legal Help (API)

  # Endpoints exercised:
  #   POST  /api/v1/bulk-submissions        — submit a bulk submission
  #   GET   /api/v1/bulk-submissions/{id}   — read persisted bulk submission
  #   PATCH /api/v1/bulk-submissions/{id}   — mark a prior submission invalid (DCLH_10) /
  #                                           drive terminal status in local mode
  #   GET   /api/v1/submissions/{id}/claims — read validation errors on rejected claims
  #                                           (DCLH_12, DCLH_13)
  #   PATCH /api/v1/claims/{id}             — void a claim (DCLH_14 only)

  # ===========================================================================
  # 1. Smoke: a static Legal Help fixture is accepted end-to-end.
  # ===========================================================================
  @smoke_single @DCLH_1
  Scenario: A Legal Help submission is accepted
    Given a Legal Help submission from file "test_upload_files/csv/outcomes.csv" for office "0U099L"
    When I submit it
    Then the submission is accepted with 1 claim


  # ===========================================================================
  # 2. Smoke: the same static fixture can be submitted twice and both are
  #    accepted (each POST returns its own bulk-submission id).
  # ===========================================================================
  @DCLH_2
  Scenario: The same submission can be uploaded twice and both are accepted
    Given a Legal Help submission from file "test_upload_files/csv/outcomes.csv" for office "0U099L"
    When I submit it
    Then the submission is accepted
    When I submit it again
    Then the submission is accepted


  # ===========================================================================
  # 3. A single generated Legal Help submission with two claims is accepted.
  # ===========================================================================
  @DCLH_3
  Scenario Outline: A generated <format> submission is accepted
    Given a Legal Help "<format>" submission with claims
      | feeCode |
      | CAPA    |
      | CAPA    |
    When I submit it
    Then the submission is accepted with 2 claims

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 4. Two claims with different UFNs in the same submission are both accepted.
  # ===========================================================================
  @DCLH_4
  Scenario Outline: Two claims in one submission with different UFNs are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        |
      | 07081996/S/ASOT | CAPA    | 030625/123 |
      | 07081996/S/ASOT | CAPA    | 030625/124 |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 5. Two separate submissions with the same claim but different UFNs are
  #    both accepted.
  # ===========================================================================
  @DCLH_5
  Scenario Outline: Two submissions with the same claim but different UFNs are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/EKOT | CAPA    | 040625/123 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/EKOT | CAPA    | 040625/124 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 6. Two claims with different UCNs in the same submission are both accepted.
  # ===========================================================================
  @DCLH_6
  Scenario Outline: Two claims in one submission with different UCNs are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        |
      | 07081996/S/EKOE | CAPA    | 010625/123 |
      | 07081997/S/EKOE | CAPA    | 010625/123 |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 7. Two separate submissions with different UCNs are both accepted.
  # ===========================================================================
  @DCLH_7
  Scenario Outline: Two submissions with different UCNs are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/UNQA | CAPA    | 060625/123 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/UNQB | CAPA    | 060625/124 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 8. Two claims with different fee codes in the same submission are both
  #    accepted (fee code differentiates otherwise-identical claims).
  # ===========================================================================
  @DCLH_8
  Scenario Outline: Two claims in one submission with different fee codes are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        |
      | 07081998/S/UNIQ | CAPA    | 070725/123 |
      | 07081998/S/UNIQ | COM     | 070725/123 |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 9. Two separate submissions with different fee codes are both accepted.
  # ===========================================================================
  @DCLH_9
  Scenario Outline: Two submissions with different fee codes are both accepted
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/FEEA | CAPA    | 080625/123 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/FEEA | COM     | 080625/124 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 10. When the previous submission was marked invalid, the same claim can
  #     be resubmitted for the same office/period and is accepted.
  # ===========================================================================
  @DCLH_10
  Scenario Outline: A submission is accepted after the previous attempt was marked invalid
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 14091962/T/EKOZ | CAPA    | 020625/100 | 0U099L |
    When I submit it
    And the previous submission is marked invalid
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 14091962/T/EKOZ | CAPA    | 020625/100 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 11. The same claim submitted for two different offices is accepted both
  #     times (office differentiates otherwise-identical claims).
  # ===========================================================================
  @DCLH_11
  Scenario Outline: The same claim submitted for two different offices is accepted both times
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/OFCA | CAPA    | 090625/123 | 1T102C |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Legal Help "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/OFCA | CAPA    | 090625/123 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


  # ===========================================================================
  # 12. Within-file duplicates: the file is accepted at upload but the
  #     event-service flags each duplicated Legal Help claim.
  # ===========================================================================
  @DCLH_12
  Scenario Outline: Two identical Legal Help claims in the same submission are rejected as duplicates
    Given a Legal Help "<format>" submission with the following claims
      | ucn   | feeCode | ufn   |
      | <ucn> | CAPA    | <ufn> |
      | <ucn> | CAPA    | <ufn> |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is rejected with the following errors
      | Error Message                                          |
      | A duplicate claim was found within the same submission |
      | A duplicate claim was found within the same submission |

    Examples:
      | format | ufn        | ucn             |
      | csv    | 010825/223 | 01021998/S/DUPA |


  # ===========================================================================
  # 13. Duplicate rule — a matching Legal Help claim resubmitted within the
  #     N-month duplicate window is rejected.
  # ===========================================================================
  @DCLH_13
  Scenario Outline: A matching Legal Help claim resubmitted within <monthsDifference> months is rejected as a duplicate
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
      | csv    | 0P322F | 05011998/S/DUPB | 020825/523 | CAPA    | 2                | A duplicate claim was found in another submission |

    Examples:
      | format | office | ucn             | ufn        | feeCode | monthsDifference | errorMessage                                      |
      | csv    | 2L849T | 03011998/S/DUPB | 010725/323 | CAPA    | 0                | Submission already exists for Office              |
      | csv    | 0P322F | 04011998/S/DUPB | 020825/423 | CAPA    | 1                | A duplicate claim was found in another submission |


  # ===========================================================================
  # 14. Voiding the first claim clears the duplicate lock, so the same Legal
  #     Help claim can be resubmitted 2 months later and is accepted.
  # ===========================================================================
  @DCLH_14
  @void
  Scenario: Voiding the first claim allows the same Legal Help claim to be resubmitted 2 months later
    Given two Legal Help "csv" submissions for office "0P322F", 2 months apart, with the following claims
      | ucn             | feeCode1 | feeCode2 | ufn        |
      | 05011998/S/DUPC | CAPA     | CAPA     | 020825/623 |
    When I submit the first submission and wait for the event service to complete the duplicate checks
    Then the first submission is accepted with 1 claim
    When I void the claim from the first submission
    And I submit the second submission and wait for the event service to complete the duplicate checks
    Then the second submission is accepted with 1 claim




