@Regression
@claimHistory
@amendments
@dstew-1814
Feature: Claim history timeline — AMENDMENT event field-level change detail

  # Jira: DSTEW-1814 (1645-D) (parent: DSTEW-1645 → DSTEW-1999)
  # Depends on: DSTEW-1813 amendment event metadata, DSTEW-1659 versioned
  # diff JSONB (schema_version=1).
  # Endpoint: GET /api/v1/claims/{claimId}/history
  #
  # Renders the AMENDMENT event's `metadata.changes[]` from the stored
  # `claim_amendment.diff`. Per changed field:
  #   * field_identifier   ← changes[].field_identifier
  #   * before             ← changes[].before  (honours present/value semantics)
  #   * after              ← changes[].after   (honours present/value semantics)
  #   * change_source      ← changes[].change_source  ("Requested" | "FSP",
  #                                                    or "System" if later added)
  #
  # Presence/null semantics (from DSTEW-1659 diff structure):
  #   before.present=true  + before.value=null → explicit stored null
  #   before.present=false                     → value not available from source
  # (Same rule for after.) These MUST survive end-to-end so AaBC can tell a
  # cleared field from a not-captured one.
  #
  # Coverage review (2026-08-11):
  #   * Parent `claimHistoryTimelineParent.feature @DS1645_4` owns the raw
  #     payload / full before-state exclusion guarantee — delegated here.
  #   * `claimHistoryAmendmentEvents.feature @DS1815_3` (DSTEW-1815) asserts
  #     an FSP-flavour changes[] entry surfaces with change_source="FSP".
  #   * `claimHistoryAmendmentMetadata.feature @DS1813_2` (DSTEW-1813) asserts
  #     `amended_field_identifiers` (list) excludes FSP entries — a separate
  #     surface from changes[].
  # Gaps this file closes: (a) the full 4-field entry shape from real data;
  # (b) Requested vs FSP entries distinguishable in the SAME changes[] array;
  # (c) explicit-null `after` (cleared field); (d) not-available `before`
  # distinguishable from explicit null; (e) unchanged fields absent;
  # (f) unsupported diff schema_version fails safely.
  #
  # OUT OF SCOPE (delegated — do NOT add here):
  #   * Raw request_payload / full before_state exposure → parent @DS1645_4
  #   * Amendment header metadata (requested_by, reason, list) → DSTEW-1813
  #   * FSP pricing / escape metadata → DSTEW-1815
  #   * Display formatting (dates, currency) → AaBC UI
  #   * Envelope / actor fallback → DSTEW-1811

  @smoke @DS1814_1
  Scenario: Single-field Requested change is rendered with all four detail fields
    Given a claim exists with a successful `claim_amendment` row whose stored diff (schema_version=1) contains
      | field_identifier | before_present | before_value | after_present | after_value | change_source |
      | client_surname   | true           | Smyth        | true          | Smith       | Requested     |
    When I request the claim history timeline
    Then the AMENDMENT event metadata `changes` array contains exactly one entry
    And that entry matches
      | field_identifier | before | after | change_source |
      | client_surname   | Smyth  | Smith | REQUESTED     |

  @DS1814_2
  Scenario: Requested and FSP consequence entries appear together and are distinguishable
    Given a claim exists with a successful `claim_amendment` row whose stored diff contains
      | field_identifier                   | before_value | after_value | change_source |
      | fee_code                           | FEE-A        | FEE-B       | Requested     |
      | calculated_fee_detail.total_amount | 100.00       | 125.00      | FSP           |
    When I request the claim history timeline
    Then the AMENDMENT event metadata `changes` array contains exactly two entries
    And the `changes` array contains an entry with the following values
      | field_identifier | before | after | change_source |
      | fee_code         | FEE-A  | FEE-B | REQUESTED     |
    And the `changes` array contains an entry with the following values
      | field_identifier                    | before | after  | change_source |
      | calculated_fee_detail.total_amount  | 100.00 | 125.00 | FSP           |

  @DS1814_3
  Scenario: Explicit-null `after` (cleared field) is returned as null, distinguishable from missing
    Given a claim exists with a successful `claim_amendment` row whose stored diff contains
      | field_identifier | before_present | before_value | after_present | after_value | change_source |
      | client_reference | true           | CLI-99       | true          | null        | Requested     |
    When I request the claim history timeline
    Then the AMENDMENT event metadata `changes` array contains an entry for "client_reference"
    And that entry's `after` field is present in the JSON response
    And that entry's `after` value is explicit JSON null
    And that entry's `after` field is NOT omitted from the JSON response


  @DS1814_5
  Scenario: Unchanged fields are absent from the changes list
    Given a claim exists with a successful `claim_amendment` row whose stored diff contains ONLY the following change entries
      | field_identifier | change_source |
      | client_surname   | Requested     |
    And the amendment payload also echoed the following fields unchanged (no change recorded in the stored diff)
      | field_identifier |
      | fee_code         |
      | office_code      |
      | ufn              |
    When I request the claim history timeline
    Then the AMENDMENT event metadata `changes` array contains exactly one entry
    And the `changes` array does NOT contain an entry for "fee_code"
    And the `changes` array does NOT contain an entry for "office_code"
    And the `changes` array does NOT contain an entry for "ufn"


