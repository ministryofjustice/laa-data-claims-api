@Full_Regression
@claims
@retentionLookup

Feature: Claim retention lookup
  As a retention service
  I want to look up valid and void claims by office code and UFN
  So that I can determine which claims match a case file

  # Endpoint: GET /api/v1/claims/retention-lookup
  # OpenAPI: claims-data/api/open-api-specification.yml, with the response schema in
  # claims-data/api/schemas/claim-retention-lookup.yml.
  #
  # The lookup uses office_code and UFN together as an exact composite key. The API returns only
  # VALID and VOID claims and exposes the minimal retention representation: claim_id, status, created_on
  # and updated_on. Results are ordered by updated_on descending, then claim_id ascending.
  # Pagination is zero-based; these scenarios explicitly request the first page (page=0, size=20)
  # and check the page metadata alongside the business result.
  #
  # Scenarios document the consumer-visible outcomes for no matches, one match and multiple
  # matches. Each scenario seeds real persisted data and calls the running service over HTTP.
  # BddHooks clears the database before each scenario, so fixtures are isolated and can run in any
  # order.
  #
  # Detailed pagination boundaries (non-zero pages, custom sizes and partial last pages), sorting,
  # identifier validation, authorization and exhaustive status filtering are covered by
  # ClaimRetentionLookupIntegrationTest. This BDD feature focuses on the core lookup outcomes and
  # does not duplicate those lower-level endpoint cases.

  Background:
    Given the retention lookup endpoint is available

  @retentionLookupNoMatch
  Scenario: No claims match the office code and UFN
    Given no claims match the retention lookup criteria
    When I look up the matching retention claims
    Then the retention response contains 0 matching claims in 0 pages
    And the retention response contains the expected matching claims

  @retentionLookupSingleMatch @Regression
  Scenario: One matching claim is returned with retention details
    Given 1 VALID claim matches the retention lookup criteria
    When I look up the matching retention claims
    Then the retention response contains 1 matching claims in 1 pages
    And the retention response contains the expected matching claims

  @retentionLookupMultipleMatches
  Scenario: Multiple matching claims are returned
    Given 3 VALID claims match the retention lookup criteria
    When I look up the matching retention claims
    Then the retention response contains 3 matching claims in 1 pages
    And the retention response contains the expected matching claims

  @retentionLookupValidAndVoid
  Scenario: VALID and VOID claims are returned with their actual status
    Given 2 VALID claims match the retention lookup criteria
    And 1 VOID claim matches the retention lookup criteria
    When I look up the matching retention claims
    Then the retention response contains 3 matching claims in 1 pages
    And the retention response contains the expected matching claims

  @retentionLookupExcludedStatus
  Scenario: Claims in other statuses are not returned
    Given 1 VOID claim matches the retention lookup criteria
    And 1 INVALID claim with the same office code and UFN is excluded from retention
    And 1 READY_TO_PROCESS claim with the same office code and UFN is excluded from retention
    When I look up the matching retention claims
    Then the retention response contains 1 matching claims in 1 pages
    And the retention response contains the expected matching claims
