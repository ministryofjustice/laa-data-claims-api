@Regression
@assessment
@stageDisbursement
@dstew-1520
Feature: Stage Disbursement assessment support on POST /assessments (BC-551)

  # Jira: DSTEW-1520  (linked delivery ticket: BC-551)
  # Endpoint: POST /api/v1/claims/{claimId}/assessments
  #
  # Additive change to the assessment type enum + reason handling:
  #   - AssessmentType gains STAGE_DISBURSEMENT_ASSESSMENT alongside
  #     ESCAPE_CASE_ASSESSMENT and VOID (VOID remains system-internal).
  #   - AaBC MUST send assessmentType AND assessmentReason for this path.
  #   - Data Stewardship PERSISTS the requestor-supplied values — never
  #     defaults a missing assessmentType, never derives assessmentReason.
  #   - Data Stewardship does NOT re-validate fee-code ↔ assessmentType
  #     eligibility (owned by AaBC per this story).
  #   - Rollout: DSTEW-1520 did NOT deliver a runtime feature flag — the new
  #     type is accepted unconditionally on the endpoint. The Background step
  #     "the stage-disbursement-assessment feature flag is enabled" is a no-op
  #     placeholder kept in the Gherkin so the scenarios read cleanly and so
  #     the phrase is ready if a runtime flag is retro-fitted.
  #
  # Eligible Stage Disbursement fee codes (business-owned list):
  #   MHLDIS, EDUDIS, ICASD, ICISD, ICSSD, ILHSD.
  #
  # Reason values (business-owned, sent verbatim by AaBC):
  #   "Stage Disbursement Assessment"
  #   "Stage Disbursement Assessment (Contingency)"
  #
  # Coverage review (2026-08-11):
  #   - `ClaimValidationService.validateAssessmentType(...)` already rejects
  #     null with `ASSESSMENT_TYPE_MUST_BE_PROVIDED_ERROR` → covered at unit
  #     layer by `ClaimValidationServiceTest`. Kept in BDD (`@DS1520_5`) as
  #     a contract-level regression guard because the message wording is
  #     called out explicitly by the ticket ("assessmentType must be
  #     provided").
  #   - No `validateAssessmentReason(null)` gate exists today — this story
  #     ADDS it. Full BDD coverage (`@DS1520_7`).
  #   - DB `V34__alter_assessment_table.sql` CHECK constraint currently
  #     allows only `ESCAPE_CASE_ASSESSMENT`, `VOID`. Migration MUST be
  #     extended by this story to include `STAGE_DISBURSEMENT_ASSESSMENT`.
  #     Persistence layer covered via `@DS1520_2..4` round-trip + explicit
  #     regression scenario `@DS1520_11`.
  #   - Existing `ESCAPE_CASE_ASSESSMENT` behaviour: covered by
  #     `AssessmentControllerIntegrationTest`, `ClaimServiceTest`,
  #     `DataClaimsApiProviderTests` (Pact) — kept ONE BDD regression
  #     (`@DS1520_9`) at contract level per DoD "existing escape assessment
  #     behaviour remains unchanged".
  #   - Claim History timeline (`claimHistoryAssessmentAndVoidEvents.feature`
  #     @DS1812_*) already renders any assessment_type verbatim including
  #     legacy null — Stage Disbursement will surface with no extra work
  #     there. Regression scenario `@DS1520_12` asserts that.
  #
  # OUT OF SCOPE (per ticket):
  #   - Fee-code ↔ assessmentType eligibility validation (AaBC-side).
  #   - Server-side defaulting/derivation of type or reason.
  #   - Escape-case model changes.
  #   - Backfill of historic assessment records.
  #   - Downstream consumer changes beyond accepting the new values.
  #   - Copy/label changes owned by AaBC.

  Background:
    Given the assessment endpoint is available
    And the stage-disbursement-assessment feature flag is enabled
    And claim "SD" exists with a Stage Disbursement fee code and status VALID
    And claim "SD" has not formally escaped

  @smoke @DS1520_1
  Scenario Outline: Eligible Stage Disbursement fee code + explicit type/reason is accepted
    Given claim "SD" has fee code "<feeCode>"
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType   | STAGE_DISBURSEMENT_ASSESSMENT       |
      | assessmentReason | Stage Disbursement Assessment       |
    Then the stage-disbursement assessment response is 201 Created
    And the persisted assessment has assessmentType "STAGE_DISBURSEMENT_ASSESSMENT"
    And the persisted assessment has assessmentReason "Stage Disbursement Assessment"
    And claim "SD" hasAssessment is true

    Examples:
      | feeCode |
      | MHLDIS  |
      | EDUDIS  |
      | ICASD   |
      | ICISD   |
      | ICSSD   |
      | ILHSD   |

  @DS1520_2
  Scenario: Non-contingency reason — persisted verbatim as supplied by AaBC
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType   | STAGE_DISBURSEMENT_ASSESSMENT       |
      | assessmentReason | Stage Disbursement Assessment       |
    Then the stage-disbursement assessment response is 201 Created
    And the persisted assessmentReason is exactly "Stage Disbursement Assessment"
    And no server-side derivation or normalisation of the reason value occurred

  @DS1520_3
  Scenario: Contingency reason — persisted verbatim as supplied by AaBC
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType   | STAGE_DISBURSEMENT_ASSESSMENT                     |
      | assessmentReason | Stage Disbursement Assessment (Contingency)       |
    Then the stage-disbursement assessment response is 201 Created
    And the persisted assessmentReason is exactly "Stage Disbursement Assessment (Contingency)"
    And the parenthesised suffix and casing are preserved byte-for-byte

  @DS1520_4
  Scenario: Assessed values persist alongside the new type / reason (no side-effects)
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType             | STAGE_DISBURSEMENT_ASSESSMENT |
      | assessmentReason           | Stage Disbursement Assessment |
      | assessed_total_incl_vat    | 875.50                        |
      | allowed_total_incl_vat     | 875.50                        |
    Then the stage-disbursement assessment response is 201 Created
    And GET on the assessment round-trips assessmentType "STAGE_DISBURSEMENT_ASSESSMENT"
    And GET on the assessment round-trips assessmentReason "Stage Disbursement Assessment"
    And GET on the assessment round-trips assessed_total_incl_vat 875.50 and allowed_total_incl_vat 875.50

  @DS1520_5
  Scenario Outline: Missing / null assessmentType rejected with the exact wording
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType   | <supplied_type>                     |
      | assessmentReason | Stage Disbursement Assessment       |
    Then the response is 400 Bad Request
    And the error message is exactly "assessmentType must be provided"
    And no assessment row is written for claim "SD"
    And claim "SD" stage-disbursement hasAssessment is unchanged

    Examples:
      | supplied_type |
      | <omitted>     |
      | null          |

  @DS1520_6
  Scenario: Unsupported assessmentType enum value rejected with the standard invalid-enum response
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType   | STAGE_DISBURSEMENT_XYZ              |
      | assessmentReason | Stage Disbursement Assessment       |
    Then the response is 400 Bad Request
    And the response matches the standard invalid assessment-type validation shape (as used for any other unknown AssessmentType enum value)
    And no assessment row is written for claim "SD"
    And Data Stewardship did NOT silently default the type to any supported value

  @DS1520_7
  Scenario Outline: Missing / null assessmentReason rejected with the exact wording
    # NEW gate introduced by this story — no existing validator today.
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType   | STAGE_DISBURSEMENT_ASSESSMENT |
      | assessmentReason | <supplied_reason>             |
    Then the response is 400 Bad Request
    And the error message is exactly "assessmentReason must be provided"
    And no assessment row is written for claim "SD"

    Examples:
      | supplied_reason |
      | <omitted>       |
      | null            |
      | <empty string>  |
      | <blank spaces>  |

  @DS1520_8
  Scenario: Data Stewardship does NOT re-validate fee-code ↔ assessmentType eligibility
    # AaBC owns this validation per the ticket. If AaBC sends
    # STAGE_DISBURSEMENT_ASSESSMENT against a non-Stage-Disbursement fee
    # code, DS accepts and persists. Negative fee-code coverage lives in
    # AaBC, NOT here.
    Given claim "NSD" has a non-Stage-Disbursement fee code (e.g. an escape-eligible fee code)
    When AaBC POSTs an assessment for claim "NSD" with
      | assessmentType   | STAGE_DISBURSEMENT_ASSESSMENT |
      | assessmentReason | Stage Disbursement Assessment |
    Then the stage-disbursement assessment response is 201 Created
    And the persisted assessmentType is "STAGE_DISBURSEMENT_ASSESSMENT"
    And Data Stewardship did NOT emit any fee-code-vs-assessmentType validation error

  @DS1520_9
  Scenario: Existing ESCAPE_CASE_ASSESSMENT journey unchanged (regression)
    Given claim "EC" has an escape-eligible fee code and is in the escape-case state
    When AaBC POSTs an assessment for claim "EC" with
      | assessmentType   | ESCAPE_CASE_ASSESSMENT   |
      | assessmentReason | Escape case reason text  |
    Then the stage-disbursement assessment response is 201 Created
    And the persisted assessment has assessmentType "ESCAPE_CASE_ASSESSMENT"
    And the persisted assessmentReason is exactly "Escape case reason text"
    And no wording in the response or contract classifies this as a Stage Disbursement assessment

  # @DS1520_10 (feature-flag guard) is intentionally commented out —
  # DSTEW-1520 shipped the type/reason acceptance and validation gates but
  # DID NOT ship a stage-disbursement-assessment feature flag. Preserved
  # so it can be un-commented as soon as the flag lands.
  # @DS1520_10
  # Scenario: Feature-flag guard — endpoint refuses the new type when the flag is off
  #   Given the stage-disbursement-assessment feature flag is disabled
  #   When AaBC POSTs an assessment for claim "SD" with
  #     | assessmentType   | STAGE_DISBURSEMENT_ASSESSMENT |
  #     | assessmentReason | Stage Disbursement Assessment |
  #   Then the request is rejected (400 or 403 per the platform's feature-flag convention)
  #   And no assessment row is written for claim "SD"
  #   And the ESCAPE_CASE_ASSESSMENT path continues to succeed while the flag is off (regression guard)

  @DS1520_11
  Scenario: Database CHECK constraint accepts the new persisted value
    # V34 originally allowed ('ESCAPE_CASE_ASSESSMENT', 'VOID'). Migration
    # delivered by this story must widen the CHECK to include the new
    # value; direct insert with the new type must succeed and be readable.
    When a Stage Disbursement assessment is persisted through the endpoint
    Then the underlying assessment row survives a repository read with
      | column          | value                          |
      | assessment_type | STAGE_DISBURSEMENT_ASSESSMENT  |
    And the read via GET /api/v1/claims/{claimId}/assessments returns the same value

  @DS1520_12
  Scenario: Claim History timeline surfaces the new type verbatim (downstream compatibility)
    # Cross-check with DSTEW-1812 @DS1812_5: assessment_type flows through
    # the metadata container as-is. New value must not be misclassified as
    # ESCAPE_CASE_ASSESSMENT or silently omitted.
    Given a Stage Disbursement assessment exists for claim "SD" with assessmentType "STAGE_DISBURSEMENT_ASSESSMENT" and assessmentReason "Stage Disbursement Assessment (Contingency)"
    When I GET /api/v1/claims/SD/history
    Then the ASSESSMENT event carries metadata "assessment_type" = "STAGE_DISBURSEMENT_ASSESSMENT"
    And the ASSESSMENT event carries metadata "assessment_reason" = "Stage Disbursement Assessment (Contingency)"
    And the event is NOT emitted as event_type "VOID"
    And the event is NOT relabelled as an escape-case assessment

  @DS1520_13
  Scenario: VOID assessment path remains system-internal — not usable through this contract
    # `validateAssessmentType` still rejects VOID sent by an external
    # caller with the existing INVALID_CLAIM_STATUS_UPDATE_MESSAGE — the
    # new value must not weaken that guard.
    When AaBC POSTs an assessment for claim "SD" with
      | assessmentType   | VOID                         |
      | assessmentReason | Anything                     |
    Then the response is 400 Bad Request
    And the error is the existing invalid-status-update message (not the new Stage Disbursement wording)
    And no assessment row is written for claim "SD"

