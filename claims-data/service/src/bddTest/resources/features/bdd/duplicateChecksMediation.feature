@Regression
@duplicateChecks
@mediation
Feature: Duplicate checks - Mediation (API)

  # Endpoints exercised:
  #   POST  /api/v1/bulk-submissions              — submit a bulk submission
  #   GET   /api/v1/bulk-submissions/{id}         — read persisted bulk submission (assertions)
  #   GET   /api/v1/bulk-submissions/{id}/summary — poll for terminal status (UAT mode)
  #   PATCH /api/v1/bulk-submissions/{id}         — mark a prior submission invalid (DCM_4) /
  #                                                 drive terminal status in local mode
  #   GET   /api/v1/validation-messages           — read submission-level validation errors
  #                                                 (DCM_2, UAT mode)

  # ===========================================================================
  # 1. Smoke: a generated Mediation submission with two claims is accepted.
  # ===========================================================================
  @DCM_1
  @smoke
  Scenario Outline: A generated Mediation <format> submission is accepted
    Given a Mediation "<format>" submission with claims
      | feeCode |
      | ASSA    |
      | ASSA    |
    When I submit it
    Then the Mediation submission is accepted with 2 claims

    Examples:
      | format |
      | xml    |


  # ===========================================================================
  # 2. Duplicate rule — resubmitting the same Mediation claim is rejected.
  # ===========================================================================
  @DCM_2
  Scenario Outline: A matching Mediation claim resubmitted is rejected as a duplicate
    Given a Mediation "<format>" submission with the following claims
      | ucn             | ufn        | feeCode | office |
      | 14091962/T/PERS | 010725/123 | ASSA    | 1T102C |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Mediation "<format>" submission with the following claims
      | ucn             | ufn        | feeCode | office |
      | 14091962/T/PERS | 010725/123 | ASSA    | 1T102C |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is rejected with the following errors
      | Error Message                                     |
      | A duplicate claim was found in another submission |

    Examples:
      | format |
      | xml    |


  # ===========================================================================
  # 3. Not a duplicate when the UCN differs across two claims in one submission.
  # ===========================================================================
  @DCM_3
  Scenario Outline: Two Mediation claims in one submission with different UCNs are both accepted
    Given a Mediation "<format>" submission with the following claims
      | ucn             | feeCode |
      | 07081996/S/EKOM | ASSA    |
      | 07081997/S/EKOM | ASSA    |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the Mediation submission is accepted

    Examples:
      | format |
      | xml    |


  # ===========================================================================
  # 4. When the previous submission was marked invalid, the same Mediation
  #    claim can be resubmitted for the same office/period and is accepted.
  # ===========================================================================
  @DCM_4
  Scenario Outline: A Mediation submission is accepted after the previous attempt was marked invalid
    Given a Mediation "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 14091962/T/OLAS | ASSA    | 020625/100 | 0U099L |
    When I submit it
    And the previous submission is marked invalid
    Given a Mediation "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 14091962/T/OLAS | ASSA    | 020625/100 | 0U099L |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the Mediation submission is accepted

    Examples:
      | format |
      | csv    |


  # ===========================================================================
  # 5. Not a duplicate when the fee code differs across two claims in one
  #    submission (Mediation fee codes ASSA vs ASST).
  # ===========================================================================
  @DCM_5
  Scenario Outline: Two Mediation claims in one submission with different fee codes are both accepted
    Given a Mediation "<format>" submission with the following claims
      | ucn            | feeCode |
      | 07081998/S/EKO | ASSA    |
      | 07081998/S/EKO | ASST    |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the Mediation submission is accepted

    Examples:
      | format |
      | txt    |


  # ===========================================================================
  # 6. Not a duplicate across two Mediation submissions for the same office
  #    and period when the fee code differs (ASSA then ASST).
  # ===========================================================================
  @DCM_6
  Scenario Outline: Two Mediation submissions with different fee codes are both accepted
    Given a Mediation "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/FEEA | ASSA    | 080625/123 | 0P322F |
    When I submit it and wait for the event service to complete the duplicate checks
    Given a Mediation "<format>" submission with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/FEEA | ASST    | 080625/124 | 0P322F |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the Mediation submission is accepted

    Examples:
      | format |
      | csv    |

