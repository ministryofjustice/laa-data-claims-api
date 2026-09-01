@Regression
@amendments
@versionGuard
@dstew-1751
Feature: Amendment request contract — claim_version JSON integer validation

  # Jira: DSTEW-1751 (parent: DSTEW-1658 → DSTEW-1999)
  # Validates the amendment payload BEFORE claim retrieval / any downstream work.
  #
  # Coverage review (2026-08-05) — DROPPED as already covered:
  #   original @DS1751_1 (valid integer accepted) →
  #     ClaimVersionValidationStepTest#shouldReturnEmptyListWhenVersionsMatch
  #
  # OUT OF SCOPE: Early stale-version gate (DSTEW-1752);
  #               Final OCC guard → amendmentsFinalSaveGuard.feature;
  #               Envelope shape (DSTEW-1754).

  Background:
    Given the amendments feature flag is enabled

  @DS1751_1
  Scenario Outline: Boundary — integer claim_version <case> is accepted at the contract layer
    Given an original claim exists at version <storedVersion>
    And the amendment payload includes claim_version <submittedVersion> as a JSON integer
    When I submit the amendment
    Then the endpoint response status is not 400
    And the response is not an "invalid claim version" request-validation error

    Examples:
      | case                | storedVersion | submittedVersion |
      | max signed 32-bit   | 1             | 2147483647       |
      | negative (semantic) | 5             | -1               |

  @DS1751_2
  Scenario Outline: Missing or non-integer claim_version is rejected with HTTP 400 — <case>
    Given an original claim exists at version 7
    When I submit an amendment with a raw JSON body where claim_version is <malformedValue>
    Then the endpoint response status is 400
    And the response uses the existing request-validation error format
    And the response does not contain a "CLAIM_VERSION_CONFLICT" code
    And no persisted claim state changed as a result of this request
    And no outbound PDA call was made
    And no outbound FSP call was made
    And no claim_amendment record was inserted for this claim by this attempt
    And no amendment before-state was computed for this claim
    And no amendment diff was computed for this claim
    And no persistence was attempted for this claim

    Examples:
      | case                       | malformedValue         |
      | field entirely missing     | absent from body       |
      | explicit null              | null                   |
      | non-numeric string         | "abc"                  |
      | quoted alphanumeric "5F"   | "5F"                   |
      | decimal number             | 7.5                    |
      | blank string               | ""                     |
      | whitespace-only string     | "   "                  |
      | empty object               | {}                     |
      | empty array                | []                     |
      | boolean true               | true                   |

  @DS1751_3
  Scenario: Bare unquoted alphanumeric 5F is malformed JSON and returns HTTP 400
    Given an original claim exists at version 7
    When I submit an amendment with a raw JSON body where claim_version is bare unquoted 5F
    Then the endpoint response status is 400
    And no persisted claim state changed as a result of this request
    And no outbound PDA call was made
    And no outbound FSP call was made
    And no claim_amendment record was inserted for this claim by this attempt

