@Regression
@amendments
@featureFlag
@dstew-1905
Feature: Amendments feature flag — application.yml gate on the whole amendments flow

  # Jira: DSTEW-1905 (parent: DSTEW-1999)
  # Endpoints: POST /api/v1/bulk-submissions, GET /api/v1/bulk-submissions/{id}/summary,
  #            PATCH /api/v1/bulk-submissions/{id}
  #
  # `feature.amendments.enabled` read from application.yml:
  #   true             → amendments processing proceeds
  #   false            → fatal submission-level rejection with the exact error string
  #   property missing → defaults to false → same fatal rejection
  # Additive: non-amendment submissions unaffected when flag is off.
  # Additive: when flag is on, existing amendment validations still apply.

  @smoke @AFF_1
  Scenario: Amendments enabled — an amendment submission is accepted
    Given the amendments feature flag is enabled
    And an amendment submission with the following claims
      | ucn             | ufn        | feeCode | office |
      | 14091962/T/PERS | 010725/123 | ASSA    | 1T102C |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the submission is accepted

  @AFF_2
  Scenario: Amendments disabled — an amendment submission is rejected with a fatal error
    Given the amendments feature flag is disabled
    And an amendment submission with the following claims
      | ucn             | ufn        | feeCode | office |
      | 14091962/T/PERS | 010725/123 | ASSA    | 1T102C |
    When I submit it and wait for the event service to validate it
    Then the submission is rejected with the following errors
      | Error Message                                                                                             |
      | Amendments processing is disabled via feature flag (feature.amendments.enabled=false). Validation failed. |

  @AFF_3
  Scenario: Amendments flag not configured — defaults to disabled and rejects the submission
    Given the amendments feature flag is not configured
    And an amendment submission with the following claims
      | ucn             | ufn        | feeCode | office |
      | 14091962/T/PERS | 010725/123 | ASSA    | 1T102C |
    When I submit it and wait for the event service to validate it
    Then the submission is rejected with the following errors
      | Error Message                                                                                             |
      | Amendments processing is disabled via feature flag (feature.amendments.enabled=false). Validation failed. |

  @AFF_4
  Scenario: Amendments disabled — a non-amendment Mediation submission is still accepted
    Given the amendments feature flag is disabled
    And a Mediation "xml" submission with the following claims
      | ucn             | ufn        | feeCode | office |
      | 07081996/S/FEEA | 080625/123 | ASSA    | 0P322F |
    When I submit it and wait for the event service to complete the duplicate checks
    Then the Mediation submission is accepted

  @AFF_5
  Scenario: Amendments enabled — existing amendment validations still apply (feature-flag error is NOT surfaced)
    Given the amendments feature flag is enabled
    And an amendment submission that is missing a required field
    When I submit it and wait for the event service to validate it
    Then the submission is rejected
    And no submission-level error contains "feature.amendments.enabled"

