@Regression
@amendments
@classifier
@dstew-1772
Feature: Amendment changed-field classifier — PDA trigger (pda_relevant)

  # Jira: DSTEW-1772 (parent: DSTEW-1646 → DSTEW-1999)
  # Feeds: DSTEW-1766 classifier via source_rule_reference.
  # Endpoints: POST /api/v1/bulk-submissions, GET /api/v1/bulk-submissions/{id}/summary
  #
  # Trigger inputs: officeCode + resolved effectiveDate.
  # Effective date driver:
  #   * Fee Code (PROD vs non-PROD selection)
  #   * Case Concluded Date (PROD)
  #   * Case Start Date > Representation Order Date > UFN (non-PROD fallback)
  # Category of Law is DERIVED — classifier triggers on inputs, not derived value.
  # ANY Fee Code change → pda_relevant=true (deliberate over-trigger, 2026-07-04).
  # All three PDA inputs unchanged → pda_relevant=false; PDA NOT called.
  #
  # OUT OF SCOPE: FSP pricing rule → amendmentsFspPricingRule.feature;
  #               PDA call/outcome → amendmentsPdaCallMechanics.feature +
  #               amendmentsPdaOutcomeMapping.feature.

  Background:
    Given the amendments feature flag is enabled
    And a positive PDA cache entry exists for the pre-amendment officeCode and resolved effectiveDate

  # ============================================================================
  # pda_relevant = true
  # ============================================================================

  @smoke @DS1772_1
  Scenario: PDA trigger — Office Code change
    Given an original claim exists with officeCode "OFC-001", feeCode "FEE-A" and resolved effectiveDate "2026-04-01"
    And an amendment updates the claim to officeCode "OFC-002"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "true"
    And the classifier source-rule reference is "OFFICE_CODE_CHANGED"
    And an outbound PDA call was made using officeCode "OFC-002" and effectiveDate "2026-04-01"

  @DS1772_2
  Scenario: PDA trigger — PROD Case Concluded Date change moves the resolved effective date
    Given an original claim exists with officeCode "OFC-001", feeCode "PROD-1", caseConcludedDate "2026-04-01" and resolved effectiveDate "2026-04-01"
    And an amendment updates the caseConcludedDate to "2026-04-15"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "true"
    And the classifier source-rule reference is "EFFECTIVE_DATE_CHANGED"
    And an outbound PDA call was made using officeCode "OFC-001" and effectiveDate "2026-04-15"

  @DS1772_3
  Scenario Outline: PDA trigger — non-PROD fallback chain resolves a new effective date
    Given an original claim exists with officeCode "OFC-001", feeCode "NONPROD-1" and non-PROD date fields
      | caseStartDate | representationOrderDate | ufn        |
      | <beforeCSD>   | <beforeROD>             | <beforeUFN>|
    And the resolved effectiveDate before amendment is "<beforeResolved>"
    And an amendment updates the non-PROD date fields to
      | caseStartDate | representationOrderDate | ufn        |
      | <afterCSD>    | <afterROD>              | <afterUFN> |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the resolved effectiveDate after amendment is "<afterResolved>"
    And the classifier output has pda_relevant "true"
    And the classifier source-rule reference is "EFFECTIVE_DATE_CHANGED"
    And an outbound PDA call was made using officeCode "OFC-001" and effectiveDate "<afterResolved>"

    Examples:
      | beforeCSD  | beforeROD  | beforeUFN | beforeResolved | afterCSD   | afterROD   | afterUFN  | afterResolved |
      | 2026-04-01 | 2026-03-01 | 010426/001| 2026-04-01     | 2026-05-01 | 2026-03-01 | 010426/001| 2026-05-01    |
      |            | 2026-03-01 | 010426/001| 2026-03-01     |            | 2026-03-15 | 010426/001| 2026-03-15    |
      |            |            | 010426/001| 2026-04-01     |            |            | 150426/001| 2026-04-15    |

  @DS1772_4
  Scenario: PDA trigger — Fee Code change alone (officeCode + effectiveDate unchanged)
    Given an original claim exists with officeCode "OFC-001", feeCode "FEE-A" and resolved effectiveDate "2026-04-01"
    And an amendment updates the feeCode to "FEE-B"
    And the resolved effectiveDate after amendment is still "2026-04-01"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "true"
    And the classifier source-rule reference is "FEE_CODE_CHANGED"
    And an outbound PDA call was made using officeCode "OFC-001" and effectiveDate "2026-04-01"

  @DS1772_5
  Scenario: PDA trigger — Fee Code change over-triggers even for same category-of-law mapping (2026-07-04 decision)
    Given an original claim exists with officeCode "OFC-001", feeCode "FEE-A" and resolved effectiveDate "2026-04-01"
    And feeCode "FEE-A" and feeCode "FEE-C" map to the same category-of-law codes
    And an amendment updates the feeCode to "FEE-C"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "true"
    And the classifier source-rule reference is "FEE_CODE_CHANGED"
    And an outbound PDA call was made using officeCode "OFC-001" and effectiveDate "2026-04-01"

  # ============================================================================
  # pda_relevant = false
  # ============================================================================
  #
  # DSTEW-2301 REVIEW ROUND 2 (2026-09-03): scenarios re-enabled.
  # -----------------------------------------------------------------------------
  # These four scenarios (@DS1772_6 .. @DS1772_9) were Type-1 commented out in
  # round 1 because "no outbound PDA call was made" was verifying the WRONG
  # boundary — a count-based assertion on ValidationService.validateClaim(Claim,
  # Set), which production always invokes exactly once per amendment PATCH.
  #
  # Copilot review pointed out (correctly) that PDA suppression is expressed in
  # the SHAPE of the Set argument, not in the call count. AmendmentExternal
  # ValidationStep.java lines 78-81:
  #
  #     Set<ClaimValidatorCode> validationCodes = new LinkedHashSet<>(...);
  #     if (!requiresPda(differences, state.getPostAmendmentState())) {
  #         validationCodes.remove(PDA_VALIDATION_STEP);           // <-- PDA skip
  #     }
  #     ...
  #     validationService.validateClaim(claim, validationCodes);
  #
  # The step "no outbound PDA call was made" now uses an ArgumentCaptor on the
  # Set argument and asserts that CLAIM_CATEGORY_OF_LAW_VALIDATOR is NOT a
  # member — the exact boundary at which production suppresses the outbound PDA
  # call. See AmendmentHarnessCommonSteps#assertValidatorSetPdaMembership.

  @DS1772_6
  Scenario: PDA skip — amendment on a non-PDA-relevant field only
    Given an original claim exists with officeCode "OFC-001", feeCode "FEE-A" and resolved effectiveDate "2026-04-01"
    And an amendment updates only the clientSurname to "Smith"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "false"
    And the classifier source-rule reference is "NO_PDA_RELEVANT_CHANGE"
    And no outbound PDA call was made
    And the prior PDA-driven validation outcome is retained

  @DS1772_7
  Scenario: PDA skip — non-PROD fallback no-op (earlier field still wins the resolved date)
    Given an original claim exists with officeCode "OFC-001", feeCode "NONPROD-1" and non-PROD date fields
      | caseStartDate | representationOrderDate | ufn        |
      | 2026-04-01    | 2026-03-01              | 010426/001 |
    And the resolved effectiveDate before amendment is "2026-04-01"
    And an amendment updates the representationOrderDate to "2026-03-15"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the resolved effectiveDate after amendment is still "2026-04-01"
    And the classifier output has pda_relevant "false"
    And the classifier source-rule reference is "NO_PDA_RELEVANT_CHANGE"
    And no outbound PDA call was made

  @DS1772_8
  Scenario: PDA skip — payload echoes a PDA-relevant field with the same value
    Given an original claim exists with officeCode "OFC-001", feeCode "FEE-A" and resolved effectiveDate "2026-04-01"
    And an amendment payload includes officeCode "OFC-001" and feeCode "FEE-A" unchanged and updates only the clientForename to "Ada"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "false"
    And no outbound PDA call was made
    And the prior PDA-driven validation outcome is retained

  @DS1772_9
  Scenario: PDA skip — all three PDA inputs unchanged even when PDA-side schedule changed post-creation
    Given an original claim exists with officeCode "OFC-001", feeCode "FEE-A" and resolved effectiveDate "2026-04-01"
    And the PDA-side contract schedule for "OFC-001" at "2026-04-01" has changed since claim creation
    And an amendment updates a non-PDA-relevant field
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "false"
    And no outbound PDA call was made
    And the prior PDA-driven validation outcome is retained

  @DS1772_10
  Scenario Outline: PDA trigger — explicit null vs omitted vs same-value vs new-value semantics on a PDA-relevant input
    Given an original claim exists with officeCode "OFC-001", feeCode "PROD-1", caseConcludedDate "2026-04-01" and resolved effectiveDate "2026-04-01"
    And an amendment supplies caseConcludedDate as <supplied>
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "<pdaRelevant>"

    Examples:
      | supplied                | pdaRelevant |
      | omitted from payload    | false       |
      | explicit null           | true        |
      | same value "2026-04-01" | false       |
      | new value "2026-04-15"  | true        |

  @DS1772_11
  Scenario Outline: PDA trigger — source-rule reference traceability across the three trigger causes
    Given an original claim exists with officeCode "OFC-001", feeCode "PROD-1", caseConcludedDate "2026-04-01" and resolved effectiveDate "2026-04-01"
    And an amendment causes the trigger cause "<cause>"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has pda_relevant "true"
    And the classifier source-rule reference is "<sourceRuleRef>"

    Examples:
      | cause                           | sourceRuleRef            |
      | Office Code changed             | OFFICE_CODE_CHANGED      |
      | Fee Code changed                | FEE_CODE_CHANGED         |
      | Resolved effective date changed | EFFECTIVE_DATE_CHANGED   |

