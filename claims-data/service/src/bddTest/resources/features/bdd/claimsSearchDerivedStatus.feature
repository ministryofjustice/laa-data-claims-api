@Regression
@claims
@readSide
@dstew-1948
Feature: Claims search — derived claim status field and sort

  # Jira: DSTEW-1948 (parent: DSTEW-1999). Companion: DSTEW-1947 (same endpoint).
  # Endpoint: GET /api/v2/claims  (paginated, office-scoped, office_code required)
  # Response field: derived_claim_status   Sort key: derived_claim_status
  # Spec: docs/derived-claim-status.md.
  #
  # Derived status is a READ-ONLY, computed business status. It is ADDITIVE —
  # the raw claim_status field, its filter and its sort key are all unchanged,
  # and the field is declared on claim_response_v2 only (v1 is untouched).
  #
  # Derivation (single owned rule, first matching rule wins — fixed business
  # precedence, NOT chronological):
  #   1. claim_status = VOID                       -> VOIDED
  #   2. claim_status = INVALID                    -> INVALID
  #   3. claim_status = READY_TO_PROCESS           -> READY_TO_PROCESS
  #   4. claim_status = VALIDATED_PENDING_APPROVAL -> VALIDATED_PENDING_APPROVAL
  #   5. has_assessment = true (claim_status VALID)-> ASSESSED
  #   6. is_amended = true     (claim_status VALID)-> AMENDED
  #   7. otherwise             (claim_status VALID)-> ACCEPTED
  #
  # RECONCILIATION with the DSTEW-1948 draft (drafted before the rule was
  # settled at refinement — behaviour below matches the SHIPPED implementation):
  #   * The draft lists FOUR values (ACCEPTED/AMENDED/ASSESSED/VOIDED). The
  #     shipped enum has SEVEN: the three extra raw-lifecycle states
  #     (INVALID, READY_TO_PROCESS, VALIDATED_PENDING_APPROVAL) are surfaced as
  #     their own derived values. This resolves the draft's Open Item "derived
  #     value for INVALID / READY_TO_PROCESS claims" — covered in DS1948_1/_5
  #     so the contract is stated, not left implicit.
  #   * The draft says ordering is "alphabetical". The shipped order is the enum
  #     DECLARATION order (DerivedClaimStatus.ordinal()). The first four happen
  #     to be alphabetical, but the full canonical order places VOIDED BEFORE
  #     INVALID / READY_TO_PROCESS / VALIDATED_PENDING_APPROVAL — enum-ordinal,
  #     NOT alphabetical. DS1948_5 asserts the real order.
  #
  # DELIBERATELY NOT a BDD scenario (documented, not silently dropped):
  #   * null has_assessment / is_amended treated as false — the Claim entity
  #     columns are primitive booleans and can never be null, so this is a
  #     resolver UNIT concern (DerivedClaimStatusResolverTest), not reachable
  #     through the persisted search path.
  #   * Exhaustive raw claim_status sort/filter "unchanged" regression — owned by
  #     the existing ClaimSpecification / ClaimController tests; here we only
  #     assert derived status is additive (DS1948_3) and does not displace the
  #     raw status field.
  #   * Computed-sort multi-field caveat (derived_claim_status must be the only
  #     sort field) — "ordering not guaranteed when combined" is not a clean
  #     behavioural assertion; it is covered by unit tests on ClaimService.
  #
  # Ordering is served in the DB across the FULL paginated result set, with a
  # deterministic secondary sort by claim.id ASC (UUIDv7) always appended.
  # Unsupported sort keys keep the endpoint's existing 400 response.
  #
  # OUT OF SCOPE: UI / display labels (BC-675); any Escaped status or
  #   escaped_case_flag change; raw claim_status lifecycle; claim history
  #   timeline (DSTEW-1645).

  Background:
    Given the v2 claims search endpoint is available

  @smoke @DS1948_1
  Scenario Outline: Derivation truth table — each claim resolves to exactly one derived status by fixed precedence
    Given a claim exists with claim_status "<claim_status>", has_assessment <has_assessment> and is_amended <is_amended>
    When I search claims
    Then that claim's derived_claim_status is "<derived>"

    Examples: VOID always wins over assessment and amendment
      | claim_status | has_assessment | is_amended | derived |
      | VOID         | false          | false      | VOIDED  |
      | VOID         | true           | true       | VOIDED  |

    Examples: raw-lifecycle states resolve to themselves regardless of flags
      | claim_status                | has_assessment | is_amended | derived                     |
      | INVALID                     | false          | false      | INVALID                     |
      | INVALID                     | true           | true       | INVALID                     |
      | READY_TO_PROCESS            | false          | false      | READY_TO_PROCESS            |
      | READY_TO_PROCESS            | true           | true       | READY_TO_PROCESS            |
      | VALIDATED_PENDING_APPROVAL  | false          | false      | VALIDATED_PENDING_APPROVAL  |
      | VALIDATED_PENDING_APPROVAL  | true           | true       | VALIDATED_PENDING_APPROVAL  |

    Examples: VALID claims differentiate on assessment then amendment
      | claim_status | has_assessment | is_amended | derived  |
      | VALID        | true           | true       | ASSESSED |
      | VALID        | true           | false      | ASSESSED |
      | VALID        | false          | true       | AMENDED  |
      | VALID        | false          | false      | ACCEPTED |

  @DS1948_2
  Scenario: Precedence is fixed, not chronological — assessment outranks amendment on a VALID claim
    Given a VALID claim that has been both amended and assessed
    When I search claims
    Then that claim's derived_claim_status is "ASSESSED"

  @DS1948_3
  Scenario: Derived status is additive — the raw claim_status is returned unchanged alongside it
    Given a VALID claim that has been assessed
    When I search claims
    Then that claim's derived_claim_status is "ASSESSED"
    And that claim's raw status field is "VALID"

  @DS1948_4
  Scenario: The derived status field is declared on v2 only — the v1 search is unaffected
    Given a VALID claim that has been amended
    When I search claims on the v2 endpoint
    Then the v2 result for that claim includes derived_claim_status "AMENDED"
    When I search claims on the v1 endpoint
    Then no claim in the v1 result carries a derived_claim_status field

  @DS1948_5
  Scenario: Ascending sort follows the canonical business order; descending is the exact reverse
    # Canonical order is enum-ordinal, NOT alphabetical: VOIDED precedes the
    # raw-lifecycle states INVALID / READY_TO_PROCESS / VALIDATED_PENDING_APPROVAL.
    Given the office has one claim in each derived status
    When I search claims sorted by "derived_claim_status" ascending
    Then the derived statuses are ordered: ACCEPTED, AMENDED, ASSESSED, VOIDED, INVALID, READY_TO_PROCESS, VALIDATED_PENDING_APPROVAL
    When I search claims sorted by "derived_claim_status" descending
    Then the derived statuses are ordered: VALIDATED_PENDING_APPROVAL, READY_TO_PROCESS, INVALID, VOIDED, ASSESSED, AMENDED, ACCEPTED

  @DS1948_6
  Scenario: Claims sharing a derived status keep a stable id order across page boundaries
    Given the office has 4 ACCEPTED claims seeded in order
    And the page size is 2
    When I request page 1 sorted by "derived_claim_status" ascending
    Then the results are the 1st and 2nd seeded claims in id order
    When I request page 2 sorted by "derived_claim_status" ascending
    Then the results are the 3rd and 4th seeded claims in id order
    And no claim is duplicated or dropped across the page boundary

  @DS1948_7
  Scenario: The derived-status sort is composable with an existing filter
    Given the office has ACCEPTED, AMENDED, ASSESSED, VOIDED and INVALID claims
    When I search claims filtered by claim_statuses "VALID" sorted by "derived_claim_status" ascending
    Then only the VALID-derived claims are returned, ordered: ACCEPTED, AMENDED, ASSESSED

  @DS1948_8
  Scenario: An unsupported sort key returns the endpoint's existing 400 response
    Given the office has one ACCEPTED claim
    When I search claims sorted by "not_a_real_sort_key" ascending
    Then the search response status is 400
    And searching sorted by "derived_claim_status" ascending is accepted with status 200

