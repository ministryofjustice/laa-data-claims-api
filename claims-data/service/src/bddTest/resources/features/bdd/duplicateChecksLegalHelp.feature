@Regression
@duplicateChecks
Feature: Duplicate checks - Legal Help (API)
  BDD integration tests covering the duplicate-check scenarios that can be
  proven purely against the HTTP API exposed by laa-data-claims-api.

  Source: the UI Cucumber feature
  bulk-submission-and-fee-scheme-tests-/tests/features/ui/BulkSubmission/duplicateChecksLegalHelp.feature

  Scope:
    * Each scenario uploads at least one bulk-submission file through the real
      multipart endpoint POST /api/v1/bulk-submissions.
    * Assertions verify the synchronous outputs of this API only — HTTP status,
      response payload, persisted bulk_submission record exposed via
      GET /api/v1/bulk-submissions/{id}, and where applicable the status
      transition driven through PATCH /api/v1/bulk-submissions/{id}.
    * Scenarios whose verdict depends on the laa-data-claims-event-service
      (claim-level duplicate detection, within-file duplicate validation
      messages, submission-period "already exists" banner, claim voiding) are
      intentionally excluded from this suite — they cannot be answered by this
      API on its own and live in the cross-service e2e suite
      (bulk-submission-and-fee-scheme-tests-).

  Background:
    Given the Legal Help duplicate checks BDD scaffold is ready

  # ===========================================================================
  # 1. Smoke uploads against the static fixture — sanity-check the multipart
  # pipeline without relying on the in-process file generator.
  # ===========================================================================
  @smoke_single @DCLH_1
  Scenario: First occurrence is accepted via API upload (static fixture)
    Given I submit Legal Help bulk file "test_upload_files/csv/outcomes.csv" for office "0U099L"
    Then the API response status should be 201
    And a bulk submission id is returned
    And the response contains 1 submission ids

  @DCLH_2
  Scenario: Same legal-help file can be submitted twice via the API
    Given I submit Legal Help bulk file "test_upload_files/csv/outcomes.csv" for office "0U099L"
    Then the API response status should be 201
    Given I re-submit the same Legal Help bulk file
    Then the API response status should be 201
    And the scenario has 2 accepted bulk uploads
    And all bulk submission ids are unique

  # ===========================================================================
  # 2. Ports of the UI scenarios whose verdict is provable from this API's
  # responses. Each one ends in either HTTP 201 plus the persisted
  # bulk_submission record exposing the expected area_of_law / outcome count,
  # or a PATCH-driven state transition followed by a second 201.
  # ===========================================================================

  @DCLH_3
  Scenario Outline: First occurrence is accepted (generated <format>)
    When I generate "Legal help" "<format>" file with the following claims
      | feeCode |
      | CAPA    |
      | CAPA    |
    And I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help" with "2" claims

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_4
  Scenario Outline: Should have no errors when UFN differs - single submission (<format>)
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        |
      | 07081996/S/ASOT | CAPA    | 030625/123 |
      | 07081996/S/ASOT | CAPA    | 030625/124 |
    When I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_5
  Scenario Outline: Should have no errors when UFN differs - two submissions (<format>)
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/EKOT | CAPA    | 040625/123 | 0U099L |
    And I upload the generated file
    And click import
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/EKOT | CAPA    | 040625/124 | 0U099L |
    When I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_6
  Scenario Outline: Should have no errors when UCN differs - single submission (<format>)
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        |
      | 07081996/S/EKOE | CAPA    | 010625/123 |
      | 07081997/S/EKOE | CAPA    | 010625/123 |
    When I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_7
  Scenario Outline: Should have no errors when UCN differs - two submissions (<format>)
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/UNQA | CAPA    | 060625/123 | 0U099L |
    And I upload the generated file
    And click import
    When I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/UNQB | CAPA    | 060625/124 | 0U099L |
    And I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_8
  Scenario Outline: Should have no errors when fee code differs - single submission (<format>)
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        |
      | 07081998/S/UNIQ | CAPA    | 070725/123 |
      | 07081998/S/UNIQ | COM     | 070725/123 |
    When I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_9
  Scenario Outline: Should have no errors when fee code differs - two submissions (<format>)
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/FEEA | CAPA    | 080625/123 | 0U099L |
    And I upload the generated file
    And click import
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/FEEA | COM     | 080625/124 | 0U099L |
    When I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_10
  Scenario Outline: Not duplicate when previous submission was invalid (<format>)
    # Exercises the PATCH /api/v1/bulk-submissions/{id} endpoint to drive the
    # first submission into VALIDATION_FAILED before re-uploading the same
    # claim. The API contract is: a second POST with the same office + period
    # is accepted (201) because the first submission no longer counts.
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 14091962/T/EKOZ | CAPA    | 020625/100 | 0U099L |
    And I upload the generated file
    And I make the generated file invalid
    And click import
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 14091962/T/EKOZ | CAPA    | 020625/100 | 0U099L |
    When I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |

  @DCLH_11
  Scenario Outline: Not duplicate when office is different across submissions (<format>)
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/OFCA | CAPA    | 090625/123 | 1T102C |
    And I upload the generated file
    And click import
    Given I generate "Legal help" "<format>" file with the following claims
      | ucn             | feeCode | ufn        | office |
      | 07081996/S/OFCA | CAPA    | 090625/123 | 0U099L |
    When I upload the generated file and wait for import in progress
    Then I should see the submission summary for "Legal help"

    Examples:
      | format |
      | csv    |
      | txt    |
      | xml    |


