@Regression
@amendments
@classifier
@dstew-1772
@dstew-1757
Feature: Amendment changed-field classifier — PDA trigger & FSP pricing rule sources

  # Consolidates two rule-source stories that populate ChangedFieldClassification:
  #   * DSTEW-1772 → pda_relevant  (parent DSTEW-1646 → DSTEW-1999)
  #   * DSTEW-1757 → impacts_pricing (parent DSTEW-1595 → DSTEW-1999)
  # Both feed DSTEW-1766 (the classifier that runs at Step 6/10 of the amendment
  # validation flow) via source_rule_reference.
  #
  # Endpoints exercised (observed at the amendment-submission seam):
  #   POST /api/v1/bulk-submissions
  #   GET  /api/v1/bulk-submissions/{id}/summary
  #
  # Behaviour under test:
  #   PDA trigger (DSTEW-1772)
  #     * PDA request inputs: officeCode + resolved effectiveDate.
  #     * Resolved effective date is driven by:
  #         - Fee Code (PROD vs non-PROD selection)
  #         - Case Concluded Date (PROD)
  #         - Case Start Date > Representation Order Date > UFN (non-PROD fallback)
  #     * Category of Law is DERIVED, so the classifier triggers on inputs
  #       (Office Code / effectiveDate / Fee Code), not on the derived value.
  #     * ANY Fee Code change → pda_relevant = true (deliberate over-trigger,
  #       decision 2026-07-04).
  #     * All three PDA inputs unchanged → pda_relevant = false; PDA is NOT
  #       called; the claim-creation PDA outcome stands. This deliberately
  #       misses PDA-side contract-schedule changes made after claim creation.
  #     * Payload echoing a value unchanged → pda_relevant = false.
  #
  #   FSP pricing rule (DSTEW-1757)
  #     * Canonical rule source = fields on the FSP fee-calculation request body.
  #     * FSP request-body rule WINS over supporting artefacts (AaBC amendable-
  #       fields, working pricing-impact views) whenever they disagree.
  #
  #   Both rule sources emit a traceable source_rule_reference on the
  #   classifier output.
  #
  # OUT OF SCOPE:
  #   * Running the classifier internals      → DSTEW-1766
  #   * Outbound PDA call mechanics           → covered in amendmentsPda.feature
  #   * PDA outcome mapping                   → covered in amendmentsPda.feature
  #   * Step 13 FSP-call decision             → DSTEW-1758
  #   * FSP request building / calling        → DSTEW-1758 / DSTEW-1759
  #   * Assessed-claim pricing rejection      → DSTEW-1767
  #
  # NOTE: these scenarios observe classifier output at the amendment-submission
  # seam (classifier diagnostic output + whether an outbound PDA call was made).
  # They do NOT unit-test the classifier internals.

  Background:
    Given the amendments feature flag is enabled
    And a positive PDA cache entry exists for the pre-amendment officeCode and resolved effectiveDate
    # ^ so trigger tests can distinguish "no trigger" from "trigger but cache hit"
    And the FSP fee-calculation request-body field map is the authoritative pricing rule source

  # ============================================================================
  # PDA trigger — pda_relevant = true
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
  # PDA trigger — pda_relevant = false
  # ============================================================================

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

  # ============================================================================
  # FSP pricing rule — impacts_pricing = true / false
  # ============================================================================

  @smoke @DS1757_1
  Scenario Outline: FSP pricing — changed field on the FSP request body is pricing-impacting
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "<field>" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference identifies the FSP request-body rule source
    And the classifier source_rule_reference includes the changed field "<field>"

    Examples:
      | field                     |
      | fee_code                  |
      | case_start_date           |
      | case_concluded_date       |
      | representation_order_date |
      | ufn                       |
      | office_code               |

  @DS1757_2
  Scenario Outline: FSP pricing — changed field NOT on the FSP request body is non-pricing
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "<field>" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"
    And the classifier source_rule_reference indicates no pricing-impacting field changed

    Examples:
      | field            |
      | client_surname   |
      | client_forename  |
      | client_reference |

  @DS1757_3
  Scenario: FSP pricing — FSP-input field with an unchanged effective value is not pricing-impacting
    Given an original claim exists with feeCode "FEE-A"
    And an amendment payload includes feeCode "FEE-A" unchanged and updates only the clientSurname to "Smith"
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"

  @DS1757_4
  Scenario: FSP pricing — a single pricing-impacting change amongst non-pricing changes still classifies as pricing-impacting
    Given an original claim exists with a valid pricing baseline
    And an amendment changes the following fields
      | field           | newValue |
      | client_surname  | Smith    |
      | client_forename | Ada      |
      | fee_code        | FEE-B    |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference includes the changed field "fee_code"

  @DS1757_5
  Scenario: FSP pricing — only non-pricing changes → impacts_pricing false
    Given an original claim exists with a valid pricing baseline
    And an amendment changes the following fields
      | field            | newValue |
      | client_surname   | Smith    |
      | client_forename  | Ada      |
      | client_reference | CLI-999  |
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"

  @DS1757_6
  Scenario: FSP pricing — supporting artefact says pricing, FSP body says no → FSP wins (false)
    Given a supporting artefact classifies the field "supporting_only_field" as pricing-impacting
    But the field "supporting_only_field" does not appear in the FSP fee-calculation request body
    And an original claim exists with a valid pricing baseline
    And an amendment changes only the field "supporting_only_field" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"
    And the classifier source_rule_reference identifies the FSP request-body rule source

  @DS1757_7
  Scenario: FSP pricing — supporting artefact says non-pricing, FSP body says yes → FSP wins (true)
    Given a supporting artefact classifies the field "fsp_only_field" as non-pricing
    But the field "fsp_only_field" appears in the FSP fee-calculation request body
    And an original claim exists with a valid pricing baseline
    And an amendment changes only the field "fsp_only_field" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference identifies the FSP request-body rule source
    And the classifier source_rule_reference includes the changed field "fsp_only_field"

  @DS1757_8
  Scenario: FSP pricing — traceable source_rule_reference for pricing-impacting result
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "fee_code" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "true"
    And the classifier source_rule_reference is "FSP_REQUEST_BODY_FIELD_CHANGED"
    And the classifier source_rule_reference identifies the FSP request-body rule source

  @DS1757_9
  Scenario: FSP pricing — traceable source_rule_reference for non-pricing result
    Given an original claim exists with a valid pricing baseline
    And an amendment changes only the field "client_surname" to a different value
    When I submit the amendment and wait for the event service to complete amendment validation
    Then the classifier output has impacts_pricing "false"
    And the classifier source_rule_reference is "NO_FSP_REQUEST_BODY_FIELD_CHANGED"

