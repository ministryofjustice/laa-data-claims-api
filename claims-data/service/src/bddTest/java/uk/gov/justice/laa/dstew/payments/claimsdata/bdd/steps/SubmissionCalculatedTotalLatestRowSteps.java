package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code submissionCalculatedTotalLatestRow.feature} (DSTEW-1644).
 *
 * <p>Verifies the submission read total ({@code GET /api/v1/submissions/{id}} → {@code
 * calculated_total_amount}) sums only the <b>latest</b> {@code calculated_fee_detail} row per
 * claim, ordered by {@code created_on DESC, id DESC}. The rule is amendment-agnostic — behaviour is
 * proven purely through row ordering, without relying on a {@code claim_amendment} FK.
 *
 * <p>Data is seeded directly through the JPA repositories (Submission → Claim → ClaimSummaryFee →
 * CalculatedFeeDetail) so scenarios stay focused on the read-side aggregation and do not depend on
 * any write-side pipeline (bulk-submission upload, event-service parsing, amendment commit).
 */
@Slf4j
public class SubmissionCalculatedTotalLatestRowSteps {

  /**
   * Known submission-response top-level JSON keys as of the pre-DSTEW-1644 contract. The
   * {@code @DS1644_5} scenario guards against silent addition of new fields (e.g. an {@code
   * is_amended} rollup or an amended-submissions banner) as part of this story.
   */
  private static final Set<String> EXPECTED_SUBMISSION_RESPONSE_KEYS =
      Set.of(
          "submission_id",
          "bulk_submission_id",
          "office_account_number",
          "submission_period",
          "area_of_law",
          "status",
          "crime_lower_schedule_number",
          "legal_help_submission_reference",
          "mediation_submission_reference",
          "previous_submission_id",
          "is_nil_submission",
          "number_of_claims",
          "submitted",
          "claims",
          "calculated_total_amount",
          "assessed_total_amount",
          "matter_starts",
          "created_by_user_id",
          "provider_user_id",
          "error_messages");

  private static final String BDD_USER_ID = "bdd-user";

  @Autowired private BddApiStepSupport api;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  // Scenario-scoped state. Cucumber instantiates one step class per scenario, so plain
  // fields are safe (no @ScenarioScope needed).
  private UUID currentSubmissionId;
  private final Map<String, UUID> claimIdByRef = new HashMap<>();
  private JsonNode lastSubmissionResponse;

  // ---------------------------------------------------------------------------
  // Given — endpoint availability + submission / claim / fee seeding
  // ---------------------------------------------------------------------------

  @Given("the submission read endpoint is available")
  public void theSubmissionReadEndpointIsAvailable() {
    // No-op background step. Availability is proven by the actual GET in "I read the submission";
    // stating it here keeps the feature file readable and gives dev a single warm-up hook.
  }

  @Given("a submission exists with the following claims")
  public void aSubmissionExistsWithTheFollowingClaims(DataTable table) {
    seedSubmission();
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    int lineNumber = 1;
    for (Map<String, String> row : rows) {
      String claimRef = row.get("claimRef");
      Claim claim = seedClaim(claimRef, lineNumber++);
      String amount = row.get("calc_fee_total_amount");
      if (StringUtils.isNotBlank(amount)) {
        seedFeeDetail(claim, new BigDecimal(amount.trim()), Instant.now(), null);
      }
    }
  }

  @Given("a submission exists with a single claim {string}")
  public void aSubmissionExistsWithASingleClaim(String claimRef) {
    seedSubmission();
    seedClaim(claimRef, 1);
  }

  @Given("a submission exists with a claim that has multiple calculated_fee_detail rows")
  public void aSubmissionExistsWithAClaimThatHasMultipleFeeRows() {
    seedSubmission();
    Claim claim = seedClaim("shape-check", 1);
    // Two rows — content values are irrelevant here, we only assert response shape.
    Instant now = Instant.now();
    seedFeeDetail(claim, new BigDecimal("100.00"), now.minusSeconds(60), null);
    seedFeeDetail(claim, new BigDecimal("125.00"), now, null);
  }

  @Given("each claim has exactly one calculated_fee_detail row")
  public void eachClaimHasExactlyOneFeeRow() {
    // Guard step — the preceding DataTable already provided one total per claim. Asserting the
    // invariant here catches drift if the fixture is reworded to omit the amount column.
    for (UUID claimId : claimIdByRef.values()) {
      long count =
          calculatedFeeDetailRepository.findAll().stream()
              .filter(cfd -> cfd.getClaim() != null && claimId.equals(cfd.getClaim().getId()))
              .count();
      assertThat(count).as("calculated_fee_detail rows for claim %s", claimId).isEqualTo(1);
    }
  }

  @Given("claim {string} has the following calculated_fee_detail rows")
  public void claimHasTheFollowingFeeRows(String claimRef, DataTable table) {
    Claim claim = requireClaim(claimRef);
    for (Map<String, String> row : table.asMaps(String.class, String.class)) {
      BigDecimal amount = new BigDecimal(row.get("total_amount").trim());
      Instant createdOn = OffsetDateTime.parse(row.get("created_on").trim()).toInstant();
      seedFeeDetail(claim, amount, createdOn, null);
    }
  }

  @Given("claim {string} has exactly one calculated_fee_detail row with total_amount {bigdecimal}")
  public void claimHasExactlyOneFeeRowWith(String claimRef, BigDecimal amount) {
    seedFeeDetail(requireClaim(claimRef), amount, Instant.now(), null);
  }

  @Given("claim {string} has the following calculated_fee_detail rows sharing created_on {string}")
  public void claimHasFeeRowsSharingCreatedOn(
      String claimRef, String createdOnIso, DataTable table) {
    Claim claim = requireClaim(claimRef);
    Instant sharedCreatedOn = OffsetDateTime.parse(createdOnIso).toInstant();

    // Deterministic UUIDs where "greater id" > "lower id" byte-wise, so the ORDER BY id DESC
    // tie-break has a stable, predictable winner regardless of insertion order.
    UUID lowerId = UUID.fromString("01900000-0000-7000-8000-000000000001");
    UUID greaterId = UUID.fromString("01900000-0000-7000-8000-000000000002");

    for (Map<String, String> row : table.asMaps(String.class, String.class)) {
      BigDecimal amount = new BigDecimal(row.get("total_amount").trim());
      String ordering = row.get("id_ordering").trim().toLowerCase();
      UUID forcedId =
          switch (ordering) {
            case "lower id" -> lowerId;
            case "greater id" -> greaterId;
            default ->
                throw new IllegalArgumentException(
                    "Unknown id_ordering value: '"
                        + ordering
                        + "'. Expected 'lower id' or 'greater id'.");
          };
      seedFeeDetail(claim, amount, sharedCreatedOn, forcedId);
    }
  }

  @Given(
      "claim {string} had an original calculated_fee_detail row with total_amount {bigdecimal}"
          + " dated {string}")
  public void claimHadAnOriginalFeeRow(String claimRef, BigDecimal amount, String createdOnIso) {
    Instant createdOn = OffsetDateTime.parse(createdOnIso).toInstant();
    seedFeeDetail(requireClaim(claimRef), amount, createdOn, null);
  }

  @Given(
      "a successful pricing amendment created a later amendment-linked calculated_fee_detail row"
          + " with total_amount {bigdecimal} dated {string}")
  public void aSuccessfulAmendmentCreatedALaterFeeRow(BigDecimal amount, String createdOnIso) {
    // DSTEW-1644 is amendment-agnostic (feature banner): the rule is "latest row per claim wins",
    // regardless of whether it was linked to a claim_amendment. Seeding another CFD with a later
    // created_on is sufficient — no need to fabricate a ClaimAmendment row.
    Claim claim =
        claimIdByRef.values().stream()
            .findFirst()
            .map(claimRepository::findById)
            .orElseThrow()
            .orElseThrow();
    Instant createdOn = OffsetDateTime.parse(createdOnIso).toInstant();
    seedFeeDetail(claim, amount, createdOn, null);
  }

  // ---------------------------------------------------------------------------
  // When
  // ---------------------------------------------------------------------------

  @When("I read the submission")
  public void iReadTheSubmission() throws IOException {
    assertThat(currentSubmissionId).as("submission must be seeded before read").isNotNull();
    lastSubmissionResponse = api.getSubmission(currentSubmissionId);
  }

  // ---------------------------------------------------------------------------
  // Then
  // ---------------------------------------------------------------------------

  @Then("the submission calculated total is {bigdecimal}")
  public void theSubmissionCalculatedTotalIs(BigDecimal expected) {
    JsonNode totalNode = lastSubmissionResponse.path("calculated_total_amount");
    assertThat(totalNode.isMissingNode() || totalNode.isNull())
        .as("calculated_total_amount must be present and non-null in the response")
        .isFalse();
    assertThat(new BigDecimal(totalNode.asText()))
        .as("calculated_total_amount in submission response")
        .isEqualByComparingTo(expected);
  }

  @Then(
      "the earlier calculated_fee_detail row for claim {string} with total_amount {bigdecimal}"
          + " did not contribute to the total")
  public void theEarlierFeeRowDidNotContribute(String claimRef, BigDecimal excludedAmount) {
    assertRowExcluded(excludedAmount);
  }

  @Then(
      "the calculated_fee_detail row with total_amount {bigdecimal} did not contribute to the"
          + " total")
  public void theFeeRowDidNotContribute(BigDecimal excludedAmount) {
    assertRowExcluded(excludedAmount);
  }

  @Then("the earlier {bigdecimal} row for claim {string} did not contribute to the total")
  public void theEarlierRowForClaimDidNotContribute(BigDecimal excludedAmount, String claimRef) {
    assertRowExcluded(excludedAmount);
  }

  @Then("the submission response shape is unchanged from today's contract")
  public void theSubmissionResponseShapeIsUnchangedFromTodaysContract() {
    assertResponseShapeMatchesKnownContract();
  }

  @Then("the submission response shape matches the pre-DSTEW-1644 contract")
  public void theSubmissionResponseShapeMatchesPreDstew1644Contract() {
    assertResponseShapeMatchesKnownContract();
  }

  @Then("the submission response contains no submission-level {string} rollup field")
  public void theSubmissionResponseContainsNoRollupField(String fieldName) {
    assertThat(lastSubmissionResponse.has(fieldName))
        .as("Unexpected submission-level rollup field '%s' present in response", fieldName)
        .isFalse();
  }

  @Then("the submission response contains no amended-submissions banner field")
  public void theSubmissionResponseContainsNoBannerField() {
    // No agreed banner field name — guard against the plausible candidates.
    List.of(
            "amended_submissions_banner",
            "amendments_banner",
            "has_amended_submissions",
            "latest_amendment_banner")
        .forEach(
            candidate ->
                assertThat(lastSubmissionResponse.has(candidate))
                    .as("Unexpected amended-submissions banner field '%s'", candidate)
                    .isFalse());
  }

  @Then(
      "the claim entries in the submission response contain no new amendment-visibility fields"
          + " introduced by this story")
  public void theClaimEntriesContainNoNewAmendmentVisibilityFields() {
    // Any of these would be a leaked amendment-visibility field this story must NOT add.
    Set<String> forbiddenClaimFields =
        Set.of("is_amended", "amendment_status", "has_amendment", "latest_amendment_id");
    JsonNode claims = lastSubmissionResponse.path("claims");
    assertThat(claims.isArray()).as("claims must be an array").isTrue();
    for (JsonNode claim : claims) {
      for (String forbidden : forbiddenClaimFields) {
        assertThat(claim.has(forbidden))
            .as("Unexpected amendment-visibility field '%s' on claim entry", forbidden)
            .isFalse();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers — data seeding & shape assertions
  // ---------------------------------------------------------------------------

  private void seedSubmission() {
    Submission submission =
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .officeAccountNumber("1644-office")
            .submissionPeriod("JAN-2025")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(BDD_USER_ID)
            .providerUserId(BDD_USER_ID)
            .createdOn(Instant.now())
            .build();
    currentSubmissionId = submissionRepository.saveAndFlush(submission).getId();
  }

  private Claim seedClaim(String claimRef, int lineNumber) {
    assertThat(currentSubmissionId).as("submission must be seeded first").isNotNull();
    Submission submissionRef = submissionRepository.findById(currentSubmissionId).orElseThrow();
    Claim claim =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(submissionRef)
            .status(ClaimStatus.VALID)
            .feeCode("TEST")
            .lineNumber(lineNumber)
            .matterTypeCode("TEST_MATTER")
            .createdByUserId(BDD_USER_ID)
            .build();
    claim = claimRepository.saveAndFlush(claim);

    // CalculatedFeeDetail is FK-linked to a ClaimSummaryFee row; seed a minimal one per claim.
    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .createdByUserId(BDD_USER_ID)
            .build();
    claimSummaryFeeRepository.saveAndFlush(summaryFee);

    if (claimRef != null) {
      claimIdByRef.put(claimRef, claim.getId());
    }
    return claim;
  }

  private void seedFeeDetail(Claim claim, BigDecimal amount, Instant createdOn, UUID forceId) {
    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository
            .findByClaimId(claim.getId())
            .orElseThrow(
                () -> new IllegalStateException("No ClaimSummaryFee for claim " + claim.getId()));
    CalculatedFeeDetail cfd =
        CalculatedFeeDetail.builder()
            .id(forceId != null ? forceId : Uuid7.timeBasedUuid())
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .totalAmount(amount)
            .createdOn(createdOn)
            .createdByUserId(BDD_USER_ID)
            .build();
    calculatedFeeDetailRepository.saveAndFlush(cfd);
  }

  private Claim requireClaim(String claimRef) {
    UUID id = claimIdByRef.get(claimRef);
    assertThat(id).as("claim '%s' must have been seeded", claimRef).isNotNull();
    return claimRepository.findById(id).orElseThrow();
  }

  private void assertRowExcluded(BigDecimal excludedAmount) {
    // Sanity: the seeded row exists (guards against typos in the scenario amount).
    boolean rowPresent =
        calculatedFeeDetailRepository.findAll().stream()
            .anyMatch(
                cfd ->
                    cfd.getTotalAmount() != null
                        && cfd.getTotalAmount().compareTo(excludedAmount) == 0);
    assertThat(rowPresent)
        .as(
            "excluded row %s must exist in the DB before we can prove it was excluded",
            excludedAmount)
        .isTrue();

    // The total assertion in the sibling "Then" already proves exclusion arithmetically. This
    // additional assertion adds an explicit anti-double-counting check: the response total must
    // NOT equal the naive SUM(all rows).
    BigDecimal naiveSumOfAllRows =
        calculatedFeeDetailRepository.findAll().stream()
            .map(CalculatedFeeDetail::getTotalAmount)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal responseTotal =
        new BigDecimal(lastSubmissionResponse.path("calculated_total_amount").asText());

    assertThat(responseTotal)
        .as(
            "submission calculated_total_amount must not include the earlier row "
                + "(naive SUM of all rows would be %s)",
            naiveSumOfAllRows)
        .isNotEqualByComparingTo(naiveSumOfAllRows);
  }

  private void assertResponseShapeMatchesKnownContract() {
    lastSubmissionResponse
        .fieldNames()
        .forEachRemaining(
            field ->
                assertThat(EXPECTED_SUBMISSION_RESPONSE_KEYS)
                    .as(
                        "Unexpected new field '%s' in submission response — DSTEW-1644 must not"
                            + " add new fields to the submission read contract",
                        field)
                    .contains(field));
  }
}
