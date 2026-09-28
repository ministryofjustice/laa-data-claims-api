package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code amendmentsDuplicateValidation.feature} (DSTEW-1769).
 *
 * <p>Real end-to-end coverage: the amendment external validation step delegates to the shared
 * claims-validation-core {@code DuplicateClaimValidator}, whose comparison set is read from the
 * database by {@code RepositoryClaimsDataProvider}. Duplicates are therefore created by DB-seeding
 * a matching claim (with a {@link Client} carrying the UCN) - not by a MockServer stub. Each
 * scenario seeds an amendable target claim (with a summary fee + calculated-fee row so the
 * before-state gate passes) plus, where needed, a colliding twin in another (or the same)
 * submission, then drives the real amendment PATCH via {@link SharedAmendmentPatchContext}.
 *
 * <p>The duplicate key is <b>office + fee code + UFN + UCN</b>; only VALID / READY_TO_PROCESS
 * claims in eligible submissions participate; a claim is never a duplicate of itself. A
 * cross-submission twin yields {@code INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION}; a
 * same-submission twin yields {@code INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION} (see {@code
 * ClaimAmendmentDuplicateValidationIntegrationTest}).
 *
 * <p>The {@code When I submit ...} phrase and the aggregate {@code rejected with the following
 * errors} / {@code each error ... Step 12} assertions are owned by sibling amendment step classes
 * and reused (they read only the scenario response context). The duplicate-specific Givens, the
 * no-duplicate / rejected-with-code / no-commit Thens, and the internal duplicate-key spec-guards
 * are owned here (the shared {@code no amendment state was committed} step is bound to another
 * class's private claim id, so this feature owns its own {@code no duplicate amendment state was
 * committed}).
 */
@Slf4j
public class AmendmentDuplicateValidationSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1769";
  private static final String REQUESTED_BY = "PROVIDER";
  private static final String REASON_CODE = "PROVIDER_ERROR";
  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-176900000001";
  private static final String DEFAULT_FEE_CODE = "CAPA";
  private static final String DEFAULT_UCN = "14091962/T/PERS";
  private static final String DEFAULT_UFN = "010725/123";
  private static final String PRIOR_PERIOD = "DEC-2024";

  // Reference-data-valid Legal Help attributes so the TARGET claim passes the full reusable
  // field validation the amendment path now runs (DSTEW-1768/2317). Values mirror
  // AmendableClaimFixture / the integration amendment suite's valid claim, so a clean amendment
  // commits (204) and a colliding amendment is rejected with ONLY the duplicate code - making the
  // duplicate assertions genuinely discriminating rather than passing on unrelated field errors.
  private static final String VALID_MATTER_TYPE_CODE = "MATT:111";
  private static final String DEFAULT_SCHEDULE_REFERENCE = "SCH-123";

  private static final String DUPLICATE_ANOTHER =
      "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION";
  private static final String DUPLICATE_SAME = "INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION";

  private static final AtomicInteger OFFICE_SEQ = new AtomicInteger();
  private static final AtomicInteger LINE_SEQ = new AtomicInteger(100);

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClaimCaseRepository claimCaseRepository;
  @Autowired private BddScenarioContext scenarioContext;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Scenario-scoped state (fresh instance per scenario).
  private String office;
  private String feeCode = DEFAULT_FEE_CODE;
  private String ufn = DEFAULT_UFN;
  private AreaOfLaw areaOfLaw;
  private boolean disbursementsOnly;
  private ObjectNode patch;

  // ---------------------------------------------------------------------------
  // Given - seed the amendable target claim
  // ---------------------------------------------------------------------------

  @Given("an original claim exists with UCN {string} and UFN {string} and area of law {string}")
  public void anOriginalClaimExistsWithUcnUfnAndAreaOfLaw(String ucn, String ufnValue, String aol) {
    areaOfLaw = AreaOfLaw.valueOf(aol.trim());
    this.ufn = ufnValue;
    provisionTarget(ucn, ufnValue, areaOfLaw);
  }

  @Given("an original claim exists with area of law {string} and outcome-code exemption {string}")
  public void anOriginalClaimExistsWithAreaOfLawAndExemption(String aol, String exemption) {
    areaOfLaw = AreaOfLaw.valueOf(aol.trim());
    // Seed the target's before-state fee type so the Legal Help disbursements-only exemption
    // applies
    // (must be set before provisionTarget, which reads the flag when building the
    // CalculatedFeeDetail).
    this.disbursementsOnly = "DISB_ONLY".equalsIgnoreCase(exemption.trim());
    provisionTarget(DEFAULT_UCN, DEFAULT_UFN, areaOfLaw);
    log.info(
        "[fixture] target claim seeded with outcome-code exemption {} (disbursementsOnly={})",
        exemption,
        disbursementsOnly);
  }

  // ---------------------------------------------------------------------------
  // Given - seed comparison / twin claims
  // ---------------------------------------------------------------------------

  @Given("no other claim exists whose duplicate key matches the post-amendment state")
  public void noOtherClaimMatchesPostAmendmentState() {
    log.info("[fixture] no colliding twin seeded - the post-amendment key is unique");
  }

  @Given("another claim exists with UCN {string} and UFN {string} and area of law {string}")
  public void anotherClaimExistsWithUcnUfnAndAreaOfLaw(String ucn, String ufnValue, String aol) {
    // Cross-submission twin: a VALID claim in a prior, validation-succeeded submission for the SAME
    // office + fee code, carrying the given UFN + UCN, so it is an eligible comparison row.
    UUID priorSubmissionId = seedSubmission(office, AreaOfLaw.valueOf(aol.trim()), PRIOR_PERIOD);
    UUID priorClaimId = seedComparisonClaim(priorSubmissionId, feeCode, ufnValue);
    seedClient(priorClaimId, ucn);
    log.info(
        "[fixture] cross-submission twin seeded (office={} feeCode={} UFN={} UCN={})",
        office,
        feeCode,
        ufnValue,
        ucn);
  }

  @Given(
      "another claim exists with the same UCN, UFN and area of law that would normally duplicate but"
          + " qualifies for the same exemption")
  public void anotherExemptClaimExists() {
    // Seed a REAL cross-submission twin on the SAME UCN/UFN key as the target. Without the
    // DISB_ONLY
    // exemption this twin would force a duplicate rejection; because the target's before-state fee
    // type is DISB_ONLY the exemption suppresses it and the amendment commits. The successful
    // commit
    // is therefore the discriminating proof the exemption was applied.
    UUID priorSubmissionId = seedSubmission(office, areaOfLaw, PRIOR_PERIOD);
    UUID priorClaimId = seedComparisonClaim(priorSubmissionId, feeCode, DEFAULT_UFN);
    seedClient(priorClaimId, DEFAULT_UCN);
    log.info(
        "[fixture] exempt colliding twin seeded (office={} feeCode={} UFN={} UCN={})",
        office,
        feeCode,
        DEFAULT_UFN,
        DEFAULT_UCN);
  }

  @Given("a sibling claim in the same submission has UCN {string} and the same UFN and fee code")
  public void aSiblingClaimInTheSameSubmission(String ucn) {
    // Same-submission twin: a VALID sibling in the TARGET submission (already validation-succeeded)
    // matching the target on fee code + UFN and carrying the UCN the amendment moves to.
    UUID siblingClaimId = seedComparisonClaim(sharedPatchContext.getSubmissionId(), feeCode, ufn);
    seedClient(siblingClaimId, ucn);
    log.info("[fixture] same-submission sibling seeded (UFN={} UCN={})", ufn, ucn);
  }

  // ---------------------------------------------------------------------------
  // Given - amendment mutations
  // ---------------------------------------------------------------------------

  @Given("an amendment updates only the field {string} to {string}")
  public void anAmendmentUpdatesOnlyTheField(String field, String value) {
    patch.put(field, value);
    publishPatch();
  }

  @Given("an amendment updates the UFN to {string}")
  public void anAmendmentUpdatesTheUfnTo(String value) {
    patch.put("unique_file_number", value);
    publishPatch();
  }

  @Given("an amendment updates the UCN to {string}")
  public void anAmendmentUpdatesTheUcnTo(String value) {
    patch.put("unique_client_number", value);
    publishPatch();
  }

  @Given("the amendment supplies an unknown amendment reason code {string}")
  public void theAmendmentSuppliesAnUnknownReasonCode(String value) {
    patch.put("amendment_reason_code", value);
    publishPatch();
  }

  // ---------------------------------------------------------------------------
  // Then - outcome assertions
  // ---------------------------------------------------------------------------

  @Then("no duplicate validation error is raised")
  public void noDuplicateValidationErrorIsRaised() {
    // The TARGET claim is seeded fully valid, so the ONLY thing that can reject the amendment is
    // the
    // duplicate gate. A clean (non-colliding) or exemption-covered amendment therefore commits with
    // a 2xx - the strict, discriminating proof that no duplicate error was raised. It can no longer
    // pass on an unrelated request-contract or field-validation failure.
    Integer status = scenarioContext.getLastStatusCode();
    String body = bodyAsString();
    assertThat(status)
        .as(
            "amendment should commit successfully when no duplicate is raised (body=%s)",
            preview(body))
        .isNotNull()
        .isBetween(200, 299);
    assertThat(body)
        .as("a committed amendment must not carry any duplicate code (body=%s)", preview(body))
        .doesNotContain(DUPLICATE_ANOTHER)
        .doesNotContain(DUPLICATE_SAME);
  }

  @Then("the amendment is rejected with error code {string}")
  public void theAmendmentIsRejectedWithErrorCode(String code) {
    Integer status = scenarioContext.getLastStatusCode();
    assertThat(status).as("rejection response status").isNotNull().isBetween(400, 499);
    assertThat(bodyAsString())
        .as("response body should carry duplicate code %s", code)
        .contains(code);
  }

  @Then("no duplicate amendment state was committed")
  public void noDuplicateAmendmentStateWasCommitted() {
    UUID claimId = sharedPatchContext.getClaimId();
    long rows =
        claimAmendmentRepository.findAll().stream()
            .filter(a -> a.getClaim() != null && claimId.equals(a.getClaim().getId()))
            .count();
    assertThat(rows)
        .as("no claim_amendment row should exist for a rejected amendment on claim %s", claimId)
        .isZero();
    Claim after = claimRepository.findById(claimId).orElseThrow();
    assertThat(after.isAmended())
        .as("claim %s must not be flagged amended after a rejected amendment", claimId)
        .isFalse();
  }

  // NOTE: The step "no amendment-related event was published for this attempt" is owned by
  // AmendmentAssessedPricingAndAmendabilitySteps (merged from main). Cucumber glue is global, so
  // defining it here too raised a DuplicateStepDefinitionException that poisoned the whole run.
  // The shared owner provides a stronger check (HTTP >=400 + zero claim_amendment rows), which is
  // fully compatible with the rejected-amendment context of the duplicate-validation scenarios.

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void publishPatch() {
    sharedPatchContext.setPatchJson(patch.toString());
  }

  private String bodyAsString() {
    JsonNode body = scenarioContext.getLastResponseBody();
    return body == null ? "" : body.toString();
  }

  private static String preview(String body) {
    return body.length() > 240 ? body.substring(0, 240) + "..." : body;
  }

  private void provisionTarget(String ucn, String ufnValue, AreaOfLaw aol) {
    office = String.format("D1%04d", OFFICE_SEQ.incrementAndGet());
    String period = periodHelper.nextAvailablePeriod(office, aol);

    // Target submission is validation-succeeded so its own claims are eligible comparison rows for
    // the same-submission scenario; for cross-submission scenarios the target submission is
    // excluded
    // from the comparison set anyway.
    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber(office)
                .submissionPeriod(period)
                .areaOfLaw(aol)
                .status(SubmissionStatus.VALIDATION_SUCCEEDED)
                .createdByUserId(SEED_ACTOR)
                .providerUserId(SEED_ACTOR)
                .createdOn(Instant.now())
                .build());

    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(submission)
                .status(ClaimStatus.VALID)
                .feeCode(feeCode)
                .lineNumber(1)
                .matterTypeCode(VALID_MATTER_TYPE_CODE)
                .scheduleReference(DEFAULT_SCHEDULE_REFERENCE)
                .uniqueFileNumber(ufnValue)
                .caseReferenceNumber("CRN-1769")
                .caseStartDate(LocalDate.of(2025, Month.JULY, 1))
                .caseConcludedDate(LocalDate.of(2025, Month.JULY, 31))
                .createdByUserId(SEED_ACTOR)
                .build());

    seedTargetClient(claim.getId(), ucn);
    seedTargetCase(claim.getId());

    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository.saveAndFlush(
            ClaimSummaryFee.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim)
                // Mandatory Legal Help fee/time fields the reusable ValidationService facade
                // requires once validateClaim runs over real HTTP. Values mirror
                // AmendableClaimFixture so the seeded TARGET claim is genuinely amendable.
                .adviceTime(60)
                .travelTime(30)
                .waitingTime(15)
                .netProfitCostsAmount(BigDecimal.valueOf(80))
                .netDisbursementAmount(BigDecimal.valueOf(13))
                .netCounselCostsAmount(BigDecimal.valueOf(35))
                .disbursementsVatAmount(BigDecimal.valueOf(2))
                .travelWaitingCostsAmount(BigDecimal.valueOf(7))
                .isVatApplicable(Boolean.TRUE)
                .createdByUserId(SEED_ACTOR)
                .createdOn(Instant.now())
                .build());

    CalculatedFeeDetail.CalculatedFeeDetailBuilder cfd =
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .feeCode(feeCode)
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now());
    if (disbursementsOnly) {
      // DISB_ONLY before-state so the Legal Help disbursements-only duplicate exemption applies
      // (DS1769_5). The exemption's observable effect is that a colliding twin raises no duplicate.
      cfd.feeType(FeeCalculationType.DISB_ONLY);
    }
    calculatedFeeDetailRepository.saveAndFlush(cfd.build());

    sharedPatchContext.setSubmissionId(submission.getId());
    sharedPatchContext.setClaimId(claim.getId());

    patch = objectMapper.createObjectNode();
    patch.put("amendment_requested_by", REQUESTED_BY);
    patch.put("amendment_reason_code", REASON_CODE);
    patch.put("amendment_user_id", AMENDMENT_USER_ID);
    patch.put("version", 0);
    publishPatch();

    log.info(
        "[fixture] target claim {} seeded (office={} feeCode={} UFN={} UCN={} AoL={})",
        claim.getId(),
        office,
        feeCode,
        ufnValue,
        ucn,
        aol);
  }

  private UUID seedSubmission(String officeAccount, AreaOfLaw aol, String period) {
    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber(officeAccount)
                .submissionPeriod(period)
                .areaOfLaw(aol)
                .status(SubmissionStatus.VALIDATION_SUCCEEDED)
                .createdByUserId(SEED_ACTOR)
                .providerUserId(SEED_ACTOR)
                .createdOn(Instant.now())
                .build());
    return submission.getId();
  }

  private UUID seedComparisonClaim(UUID submissionId, String feeCodeValue, String ufnValue) {
    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(submissionRepository.getReferenceById(submissionId))
                .status(ClaimStatus.VALID)
                .feeCode(feeCodeValue)
                .lineNumber(LINE_SEQ.incrementAndGet())
                .matterTypeCode("MAT01")
                .uniqueFileNumber(ufnValue)
                .caseReferenceNumber("CMP-CRN")
                .createdByUserId(SEED_ACTOR)
                .build());
    return claim.getId();
  }

  private void seedClient(UUID claimId, String ucn) {
    clientRepository.saveAndFlush(
        Client.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRepository.getReferenceById(claimId))
            .clientForename("Dup")
            .clientSurname("Licate")
            .uniqueClientNumber(ucn)
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());
  }

  /**
   * Seeds the TARGET claim's {@link Client} with the full set of mandatory Legal Help attributes
   * the reusable field validation requires, so a clean amendment commits (204). Comparison twins
   * use the lighter {@link #seedClient} because they are never validated - they only supply a
   * duplicate-key comparison row.
   */
  private void seedTargetClient(UUID claimId, String ucn) {
    clientRepository.saveAndFlush(
        Client.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRepository.getReferenceById(claimId))
            .clientForename("Jane")
            .clientSurname("Smith")
            .clientDateOfBirth(LocalDate.of(1990, Month.JANUARY, 1))
            .uniqueClientNumber(ucn)
            .clientPostcode("SW1H 9HE")
            .genderCode("F")
            .ethnicityCode("99")
            .disabilityCode("COG")
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());
  }

  /** Seeds the mandatory {@link ClaimCase} attributes the reusable field validation requires. */
  private void seedTargetCase(UUID claimId) {
    claimCaseRepository.saveAndFlush(
        ClaimCase.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRepository.getReferenceById(claimId))
            .caseId("123")
            .uniqueCaseId("UC_ID_" + LINE_SEQ.incrementAndGet())
            .outcomeCode("AB")
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());
  }
}
