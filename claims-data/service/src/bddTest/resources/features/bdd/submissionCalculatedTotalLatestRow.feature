@Regression
@submission
@readSide
@dstew-1644
Feature: Submission calculated total — sum only the latest calculated_fee_detail row per claim

  # Jira: DSTEW-1644 (parent: DSTEW-1999)
  # Endpoint: GET /api/v1/submissions/{id}          (submission read)
  #
  # Read-side correctness fix that must land BEFORE or WITH DSTEW-1659
  # (one-to-many `calculated_fee_detail`). If DSTEW-1659 lands first, a
  # submission containing a pricing-amended claim double-counts by summing
  # original + amended calc-fee rows.
  #
  # Latest-row rule (must match the DSTEW-1659 index / DSTEW-1815 pattern):
  #   ORDER BY created_on DESC, id DESC LIMIT 1  (per claim, then SUM).
  # Rule is amendment-agnostic — does NOT depend on `claim_amendment_id`
  # or `is_price_changed`. Simply: latest row per claim wins.
  #
  # Coverage review (2026-08-11): existing `SubmissionController` integration
  # tests assert the single-row behaviour, which becomes the AC1 regression
  # baseline. Multi-row / tie-break behaviour has no existing test — this
  # ticket is the first time > 1 calc-fee row per claim is legal.
  #
  # OUT OF SCOPE: new search / detail / banner fields (explicitly excluded);
  #               claim history endpoint → DSTEW-1645;
  #               CSV export → DSTEW-1655;
  #               one-to-many schema itself → DSTEW-1659.

  Background:
    Given the submission read endpoint is available

  @smoke @DS1644_1
  Scenario: Regression — single-row-per-claim submissions keep today's calculated total
    Given a submission exists with the following claims
      | claimRef | calc_fee_total_amount |
      | C-1      | 100.00                |
      | C-2      | 200.00                |
      | C-3      | 50.00                 |
    And each claim has exactly one calculated_fee_detail row
    When I read the submission
    Then the submission calculated total is 350.00
    And the submission response shape is unchanged from today's contract

  @DS1644_2
  Scenario: Multi-row amended claim — only the latest calc-fee row per claim contributes
    Given a submission exists with the following claims
      | claimRef |
      | X        |
      | Y        |
    And claim "X" has the following calculated_fee_detail rows
      | total_amount | created_on           | note                    |
      | 100.00       | 2026-04-01T10:00:00Z | original submission row |
      | 125.00       | 2026-05-02T09:14:00Z | amendment-linked row    |
    And claim "Y" has exactly one calculated_fee_detail row with total_amount 200.00
    When I read the submission
    Then the submission calculated total is 325.00
    And the earlier calculated_fee_detail row for claim "X" with total_amount 100.00 did not contribute to the total

  @DS1644_3
  Scenario: Deterministic tie-break — same created_on, greatest id wins
    Given a submission exists with a single claim "T"
    And claim "T" has the following calculated_fee_detail rows sharing created_on "2026-05-02T09:14:00Z"
      | total_amount | id_ordering    |
      | 100.00       | lower id       |
      | 175.00       | greater id     |
    When I read the submission
    Then the submission calculated total is 175.00
    And the calculated_fee_detail row with total_amount 100.00 did not contribute to the total

  @DS1644_4
  Scenario: Successful pricing amendment — submission total reflects post-amendment value
    Given a submission exists with a single claim "P"
    And claim "P" had an original calculated_fee_detail row with total_amount 100.00 dated "2026-04-01T10:00:00Z"
    And a successful pricing amendment created a later amendment-linked calculated_fee_detail row with total_amount 125.00 dated "2026-05-02T09:14:00Z"
    When I read the submission
    Then the submission calculated total is 125.00
    And the earlier 100.00 row for claim "P" did not contribute to the total

  @DS1644_5
  Scenario: Contract shape — no amendment / banner / rollup fields are added by this story
    Given a submission exists with a claim that has multiple calculated_fee_detail rows
    When I read the submission
    Then the submission response contains no submission-level "is_amended" rollup field
    And the submission response contains no amended-submissions banner field
    And the claim entries in the submission response contain no new amendment-visibility fields introduced by this story
    And the submission response shape matches the pre-DSTEW-1644 contract

