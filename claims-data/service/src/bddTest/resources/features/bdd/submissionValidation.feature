@Regression
@validationChecks
Feature: Submission-level validation (API)

  # Endpoints exercised:
  #   POST  /api/v1/bulk-submissions             — submit a bulk submission
  #   GET   /api/v1/bulk-submissions/{id}/summary — poll for terminal status (UAT mode)
  #   PATCH /api/v1/bulk-submissions/{id}        — drive terminal status in local mode
  #   GET   /api/v1/validation-messages          — read submission-level validation errors
  #                                                (UAT mode)

  # ===========================================================================
  # 1. Submissions for periods before JAN-2015 are rejected outright.
  #    Fixture is a minimal, zero-claim TXT with submissionPeriod=AUG-2014.
  # ===========================================================================
  @SV_1
  @smoke
  Scenario: A submission for a period before JAN-2015 is rejected
    Given a submission fixture "test_upload_files/txt/invalid_submission_period_pre2015.txt"
    When I submit it and wait for the event service to validate it
    Then the submission is rejected with the following errors
      | Error Message                                                                                             |
      | Submissions for periods before JAN-2015 are not accepted. Please submit for a period on or after JAN-2015. |


  # ===========================================================================
  # 2. Submissions for the current month, or any future month, are rejected.
  #    The step mutates submissionPeriod=... in the fixture to the current
  #    month (MMM-YYYY) or one month ahead before uploading. The literal
  #    <CURRENT_MONTH> placeholder in the expected error text is resolved to
  #    the actual month label at assertion time.
  # ===========================================================================
  @SV_2
  Scenario Outline: A submission for the <periodKind> is rejected
    Given a submission fixture "test_upload_files/txt/invalid_submission_period_pre2015.txt" with the submission period set to the "<periodKind>"
    When I submit it and wait for the event service to validate it
    Then the submission is rejected with the following errors
      | Error Message  |
      | <errorMessage> |

    Examples:
      | periodKind    | errorMessage                                                                                                    |
      | current month | Submissions for the current month (<CURRENT_MONTH>) are not accepted. Please submit for a previous month.       |
      | future month  | Submissions for after the current month (<CURRENT_MONTH>) are not accepted. Please submit for a previous month. |

