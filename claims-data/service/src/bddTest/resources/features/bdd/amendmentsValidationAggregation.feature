@Regression
@amendments
@stepTwelve
@dstew-1770
Feature: Amendment Step 12 — validation-message aggregation & outcome check

  # Jira: DSTEW-1770 (parent: DSTEW-1593 → DSTEW-1999)
  # Endpoint: PATCH /api/v1/submissions/{submissionId}/claims/{claimId}  (DSTEW-1593)
  #
  # Owns the Step 12 glue: aggregate COLLECTED validation messages from the
  # upstream sources and decide the flow outcome BEFORE FSP is called.
  #
  # Aggregation sources (with the REAL shipped error code each raises — the
  # DSTEW-1999 draft used placeholder codes for three of them; reconciled here
  # against ClaimAmendmentValidationCode + claims-validation-core +
  # AmendmentDuplicateValidationSteps constants):
  #   * DSTEW-1765 metadata validation        → INVALID_REQUESTED_BY_UNKNOWN (unknown requested-by code;
  #                                              draft "INVALID_USER_IDENTIFIER_FORMAT" needs a malformed
  #                                              user id which Jackson rejects at parse, so cannot aggregate)
  #   * DSTEW-1767 field-level amendability    → INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW
  #   * DSTEW-1768 reusable field validation   → SCHEMA_VALIDATION_ERROR
  #                                              (draft "INVALID_FIELD_VALUE" is not shipped)
  #   * DSTEW-1774 PDA VALIDATION failure      → INVALID_AREA_OF_LAW_FOR_PROVIDER
  #                                              (proven aggregating in its own DSTEW-1774 file, which
  #                                              owns the non-matching /schedules fixture; DS1770_1
  #                                              co-triggers the other four sources)
  #   * DSTEW-1769 duplicate validation        → INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION
  #                                              (draft "DUPLICATE_CLAIM" is not shipped)
  #
  # Rules (see AmendmentExternalValidationStep + the amendment validation pipeline):
  #   * Any collected ERROR-severity message → amendment rejected, FSP NOT called,
  #     nothing saved. Non-fatal ERROR issues do NOT stop the pipeline, so issues
  #     from several sources aggregate into one multi-message response.
  #   * No collected messages → flow continues to Step 13 FSP trigger.
  #   * A FATAL terminal failure (fee-code Area-of-Law gate, metadata reference
  #     data down, OCC 409) short-circuits the pipeline — collected messages are
  #     NOT mixed into the terminal response.
  #
  # OUT OF SCOPE: individual per-field message wording;
  #               terminal technical response shape → owning integration tickets.

  Background:
    Given the amendments feature flag is enabled

  # ============================================================================
  # Cross-source aggregation (parent-level guarantee)
  # ============================================================================

  @smoke @DS1770_1
  Scenario: Messages from multiple aggregation sources are returned together in one Step 12 response
    Given an original claim exists with area of law "LEGAL_HELP"
    And the field "representation_order_date" is NOT on the AaBC amendable-fields list for area of law "LEGAL_HELP"
    And a colliding sibling claim exists whose duplicate key matches the post-amendment state
    And an amendment is submitted that triggers the following collected failures
      | source                    | expectedErrorCode                              |
      | metadata (DSTEW-1765)     | INVALID_REQUESTED_BY_UNKNOWN                   |
      | amendability (DSTEW-1767) | INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW    |
      | reusable (DSTEW-1768)     | SCHEMA_VALIDATION_ERROR                        |
      | duplicate (DSTEW-1769)    | INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with the following errors in any order
      | Error Code                                     |
      | INVALID_REQUESTED_BY_UNKNOWN                   |
      | INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW    |
      | SCHEMA_VALIDATION_ERROR                        |
      | INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION |
    And each error is returned in the shared Step 12 multi-message response
    And the aggregated response strictly carries each collected code
    And no amendment state was committed

  # ============================================================================
  # Outcome check — any collected error blocks FSP and persistence
  # ============================================================================

  @DS1770_2
  Scenario: Any collected validation error → FSP not called, nothing saved
    Given an original claim exists with area of law "LEGAL_HELP"
    And an amendment is submitted that triggers a single collected error with code "SCHEMA_VALIDATION_ERROR"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is rejected with error code "SCHEMA_VALIDATION_ERROR"
    And no outbound FSP call was made from the amendment harness
    And no claim_amendment record was inserted for this claim by this attempt
    And the claim persisted state matches the pre-amendment state

  @DS1770_3
  Scenario: No collected validation errors → flow continues to Step 13 FSP trigger
    Given an original claim exists with area of law "LEGAL_HELP"
    And the FSP service will return a valid fee calculation for the amendment
    And an amendment is submitted that triggers no collected validation error and impacts pricing
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the amendment is accepted
    And exactly 1 outbound FSP call was made

  # ============================================================================
  # Terminal-vs-collected mutual exclusion (parent-level guarantee)
  # ============================================================================

  @DS1770_4
  Scenario Outline: A terminal <terminalKind> failure short-circuits aggregation — collected messages are NOT mixed in
    Given an original claim exists with area of law "LEGAL_HELP"
    And an amendment is submitted that would also collect code "SCHEMA_VALIDATION_ERROR" but hits terminal "<terminalKind>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the endpoint responds with a controlled terminal failure "<terminalCode>"
    And the response strictly carries terminal code "<terminalCode>"
    And the response does not contain a validation message with code "SCHEMA_VALIDATION_ERROR"
    And no amendment state was committed

    # NOTE: the metadata-reference-data-down terminal (TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA)
    # is proven by DSTEW-1765's own terminal scenario; it is omitted here because reproducing it needs
    # destructive reference-table mutation whose cross-scenario restore is owned by the @dstew-1765
    # hooks, not this file. The two examples below prove the same terminal-vs-collected mutual
    # exclusion guarantee (a FATAL short-circuit discards collected messages) via clean, real gates.
    Examples:
      | terminalKind                | terminalCode                        |
      | fee-code Area-of-Law change | INVALID_FEE_CODE_AREA_OF_LAW_CHANGE |
      | OCC version conflict        | CLAIM_VERSION_CONFLICT              |

  # ============================================================================
  # Envelope-shape guarantee
  # ============================================================================

  @DS1770_5
  Scenario: The Step 12 multi-message response uses the existing structured validation/error envelope shape
    Given an original claim exists with area of law "LEGAL_HELP"
    And an amendment is submitted that omits the required metadata fields
      | Error Code                       |
      | INVALID_REQUESTED_BY_MISSING     |
      | INVALID_AMENDMENT_REASON_MISSING |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the response envelope matches the existing structured validation error contract
    And each message includes a distinct error code
    And each message preserves the user-displayable text supplied by the source validator





