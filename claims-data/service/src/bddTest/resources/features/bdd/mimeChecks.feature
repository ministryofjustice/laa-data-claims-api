@Regression
@mimeChecks
Feature: MIME validation checks (API)
  API port of the UI Cucumber feature
  bulk-submission-and-fee-scheme-tests-/tests/features/ui/BulkSubmission/mimeChecks.feature

  Each scenario generates a fresh Legal Help bulk-submission payload, uploads it
  through POST /api/v1/bulk-submissions with an explicit multipart Content-Type
  header and verifies the synchronous validation enforced by
  BulkSubmissionFileValidator:
    * Allowed (extension, mime) pairs -> HTTP 201 + bulk_submission_id
    * Disallowed pairs                -> HTTP 415 + error body
      "The selected file must be a valid CSV, XML or TXT file"

  The UI-only "wait on validation in progress screen" step is dropped — for the
  API the synchronous response is sufficient.

  Background:
    Given the bulk submission MIME checks scaffold is ready

  @MC_1
  Scenario Outline: Accept submission when <format> has Mime Type <mimeType>
    Given I generate "Legal help" "<format>" file with "1" outcomes
    When I upload the generated file with mime type "<mimeType>"
    Then I should see the submission summary for "Legal help"

    Examples:
      | format | mimeType                 |
      | txt    | text/plain               |
      | csv    | text/plain               |
      | csv    | text/csv                 |
      | xml    | text/xml                 |
      | csv    | application/vnd.ms-excel |

  @MC_2
  Scenario Outline: Reject submission when <format> has Mime Type <mimeType>
    Given I generate "Legal help" "<format>" file with "1" outcomes
    When I upload the generated file with mime type "<mimeType>"
    Then the user sees an error message "The selected file must be a valid CSV, XML or TXT file"

    Examples:
      | format | mimeType                 |
      | txt    | application/xml          |
      | txt    | text/xml                 |
      | txt    | text/csv                 |
      | txt    | application/csv          |
      | txt    | application/vnd.ms-excel |
      | txt    | application/json         |
      | txt    | application/pdf          |
      | csv    | application/xml          |
      | csv    | text/xml                 |
      | csv    | text/html                |
      | csv    | application/json         |
      | xml    | text/csv                 |
      | xml    | text/plain               |
      | xml    | application/vnd.ms-excel |
      | xml    | application/csv          |
      | xml    | application/json         |

