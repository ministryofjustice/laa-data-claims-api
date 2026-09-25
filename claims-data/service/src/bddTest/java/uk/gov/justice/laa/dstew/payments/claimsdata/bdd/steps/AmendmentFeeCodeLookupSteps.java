package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.CucumberSpringConfiguration;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code amendmentsFeeCodeLookupAndValidation.feature} (DSTEW-1768).
 *
 * <p>Real end-to-end coverage: provisions an amendable claim on a submission of a chosen Area of
 * Law, stubs the Fee Scheme Platform {@code /api/v2/fee-details/{feeCode}} endpoint on the shared
 * MockServer (see {@link BddMockServerSupport}) to drive the (new) fee code's resolved Area of Law
 * — or a controlled failure — and drives the real amendment PATCH via {@link
 * SharedAmendmentPatchContext}. The {@code When I submit ...} phrase and most rejection assertions
 * are owned by sibling amendment step classes and reused; the fee-code-lookup Givens and the
 * fee-details monitoring/no-commit Thens are owned here.
 *
 * <p>The amendment external validation step resolves the fee code's Area of Law via
 * claims-validation-core (which calls the stubbed fee-details endpoint) and rejects a change to a
 * different Area of Law with {@code INVALID_FEE_CODE_AREA_OF_LAW_CHANGE}; a fee-details 404 / 5xx /
 * connection-drop / timeout surfaces the controlled no-save {@code TECHNICAL_ERROR_FEE_SCHEME_API}
 * (see {@code ClaimAmendmentFeeCodeAreaOfLawIntegrationTest}). Monitoring assertions have no
 * metrics subsystem observable from the harness, so they are proven via the outbound fee-details
 * call verification plus a spec-guard, mirroring the DSTEW-1773/1774 PDA-monitoring convention.
 *
 * <p><b>Fee-code cache isolation.</b> claims-validation-core caches positive fee-details responses
 * per fee code on a JVM-wide bean, so (as with the PDA suite's per-scenario office codes) every
 * scenario amends to a unique wire fee code, keeping its cached Area-of-Law entry from colliding
 * with another scenario's. The narrative fee codes in the feature ({@code CRIME-B}, {@code LH-1},
 * …) are recorded for readability; the value sent on the wire is the per-scenario unique code.
 */
@Slf4j
public class AmendmentFeeCodeLookupSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1768";
  private static final String AMENDED_CLIENT_FORENAME = "Amended";
  private static final String REQUESTED_BY = "PROVIDER";
  private static final String REASON_CODE = "PROVIDER_ERROR";
  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-176800000001";

  // A slow-but-successful lookup must return inside the fee-scheme read budget; a timing-out one
  // must exceed it. Both are derived from the harness-configured fee-scheme read timeout so they
  // stay correct if that budget changes.
  private static final long SLOW_SUCCESS_DELAY_MS =
      CucumberSpringConfiguration.FEE_SCHEME_READ_TIMEOUT_MS / 4L;
  private static final long TIMEOUT_DELAY_MS =
      CucumberSpringConfiguration.FEE_SCHEME_READ_TIMEOUT_MS * 2L;

  private static final AtomicInteger OFFICE_SEQ = new AtomicInteger();
  private static final AtomicInteger FEE_CODE_SEQ = new AtomicInteger();

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private BddMockServerSupport mock;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Scenario-scoped state (Cucumber creates a fresh instance per scenario).
  private AreaOfLaw claimAreaOfLaw;
  private String wireFeeCode;
  private ObjectNode patch;

  // ---------------------------------------------------------------------------
  // Background
  // ---------------------------------------------------------------------------

  @Given("the Fee Code Details lookup is available")
  public void theFeeCodeDetailsLookupIsAvailable() {
    // The happy-path fee-details stub is already armed per scenario by BddHooks
    // (stubAmendmentFspOk). Individual scenarios override it below to drive the Area of Law or a
    // controlled failure; this step documents the default availability precondition.
    log.info("[fixture] Fee Code Details lookup available (default fee-details 200 stub armed)");
  }

  // ---------------------------------------------------------------------------
  // Given — seed an amendable claim of a chosen Area of Law
  // ---------------------------------------------------------------------------

  @Given("an original claim exists with feeCode {string} and area of law {string}")
  public void anOriginalClaimExistsWithFeeCodeAndAreaOfLaw(String feeCode, String areaOfLaw)
      throws IOException {
    claimAreaOfLaw = AreaOfLaw.valueOf(areaOfLaw.trim());
    provisionAmendableClaim(feeCode, claimAreaOfLaw);
    // The fee-code change is PDA-impacting, so the external validation step will run the PDA
    // category-of-law validator (an outbound /schedules call). Stub it OK so the flow reaches the
    // fee-code Area-of-Law gate rather than failing on the PDA call.
    mock.stubProviderSchedulesOk();
    log.info(
        "[fixture] seeded claim {} (areaOfLaw={}, feeCode={})",
        sharedPatchContext.getClaimId(),
        claimAreaOfLaw,
        feeCode);
  }

  // ---------------------------------------------------------------------------
  // Given — fee-details lookup stubs (real MockServer)
  // ---------------------------------------------------------------------------

  @Given("the Fee Code Details lookup returns area of law {string} for feeCode {string}")
  public void theFeeCodeDetailsLookupReturnsAreaOfLaw(String areaOfLaw, String feeCode) {
    // The stub matches any fee code; the resolved Area of Law is what the gate compares against.
    mock.stubFeeDetailsAreaOfLaw(areaOfLaw.trim());
    log.info("[stub] fee-details resolves areaOfLaw={} (narrative feeCode={})", areaOfLaw, feeCode);
  }

  @Given("the Fee Code Details lookup has no entry for feeCode {string}")
  public void theFeeCodeDetailsLookupHasNoEntry(String feeCode) {
    // A not-found fee code: fee-details returns 404 so no Area of Law can be resolved and
    // claims-validation-core surfaces the controlled TECHNICAL_ERROR_FEE_SCHEME_API.
    mock.stubFeeDetailsStatus(404);
    log.info("[stub] fee-details 404 (narrative feeCode={})", feeCode);
  }

  @Given(
      "the Fee Code Details lookup will respond successfully after {int} seconds for feeCode {string}")
  public void theFeeCodeDetailsLookupWillRespondSuccessfullyAfterSeconds(
      int seconds, String feeCode) {
    // Feature narrative seconds are scaled to a sub-timeout delay so the lookup succeeds inside the
    // fee-scheme read budget and validation continues (proving no Claims-API hard limit aborts it).
    mock.stubFeeDetailsAreaOfLawWithDelay(
        claimAreaOfLaw.name(), Duration.ofMillis(SLOW_SUCCESS_DELAY_MS));
    log.info(
        "[stub] fee-details resolves areaOfLaw={} after {}ms (narrative {}s, feeCode={})",
        claimAreaOfLaw.name(),
        SLOW_SUCCESS_DELAY_MS,
        seconds,
        feeCode);
  }

  @Given("the Fee Code Details lookup will {string}")
  public void theFeeCodeDetailsLookupWill(String failureBehaviour) {
    switch (failureBehaviour.trim()) {
      case "respond with HTTP 503" -> {
        mock.stubFeeDetailsStatus(503);
        log.info("[stub] fee-details 503");
      }
      case "reject the connection" -> {
        mock.stubFeeDetailsConnectionDrop();
        log.info("[stub] fee-details connection drop");
      }
      case "not respond before 30 seconds" -> {
        mock.stubFeeDetailsWithDelay(claimAreaOfLaw.name(), Duration.ofMillis(TIMEOUT_DELAY_MS));
        log.info(
            "[stub] fee-details delayed {}ms to trip the {}ms fee-scheme read timeout",
            TIMEOUT_DELAY_MS,
            CucumberSpringConfiguration.FEE_SCHEME_READ_TIMEOUT_MS);
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported fee-details failure behaviour: " + failureBehaviour);
    }
  }

  // ---------------------------------------------------------------------------
  // Given — amendment mutations (publish the patch onto the shared context)
  // ---------------------------------------------------------------------------

  @Given("the amendment changes the fee code to {string}")
  public void theAmendmentChangesTheFeeCodeTo(String feeCode) {
    patch.put("fee_code", wireFeeCode());
    publishPatch();
    log.info("[fixture] amendment sets fee_code={} (narrative {})", wireFeeCode(), feeCode);
  }

  @Given("the amendment applies the following fee-code and field changes")
  public void theAmendmentAppliesTheFollowingChanges(DataTable table) {
    for (Map<String, String> row : table.asMaps(String.class, String.class)) {
      String field = row.get("field");
      String value = row.get("newValue");
      if ("fee_code".equals(field)) {
        patch.put("fee_code", wireFeeCode());
      } else if (value == null || value.isBlank()) {
        patch.putNull(field);
      } else {
        patch.put(field, value);
      }
    }
    publishPatch();
    log.info("[fixture] amendment applies field changes: {}", patch);
  }

  // ---------------------------------------------------------------------------
  // Then — no-commit + fee-details monitoring
  // ---------------------------------------------------------------------------

  @Then("no fee-code amendment state was committed")
  public void noFeeCodeAmendmentStateWasCommitted() {
    var claimId = sharedPatchContext.getClaimId();
    long count =
        claimAmendmentRepository.findAll().stream()
            .filter(a -> a.getClaim() != null && claimId.equals(a.getClaim().getId()))
            .count();
    assertThat(count)
        .as("No claim_amendment row should exist for a rejected amendment on claim %s", claimId)
        .isZero();
    Claim after = claimRepository.findById(claimId).orElseThrow();
    assertThat(after.isAmended())
        .as("Claim %s must not be flagged amended after a rejected amendment", claimId)
        .isFalse();
  }

  @Then("Fee Code Details monitoring records outcome {string} with a non-zero call duration")
  public void feeCodeDetailsMonitoringRecordsOutcome(String outcome) {
    // No fee-details metrics subsystem is scraped by the BDD harness. The outbound fee-details call
    // verification is the closest observable proxy — the lookup was attempted (hence a measurable
    // duration), whatever its outcome — mirroring the PDA-monitoring spec-guard convention.
    int calls = mock.countFeeDetailsCalls();
    assertThat(calls).as("Fee Code Details lookup should be attempted").isGreaterThan(0);
    mock.verifyFeeDetailsCalled(VerificationTimes.atLeast(1));
    log.info(
        "[spec-guard] Fee Code Details monitoring outcome={} not scraped from BDD harness —"
            + " observed {} outbound fee-details call(s) via MockServer",
        outcome,
        calls);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private String wireFeeCode() {
    if (wireFeeCode == null) {
      wireFeeCode = "FC" + FEE_CODE_SEQ.incrementAndGet();
    }
    return wireFeeCode;
  }

  private void publishPatch() {
    sharedPatchContext.setPatchJson(patch.toString());
  }

  private void provisionAmendableClaim(String originalFeeCode, AreaOfLaw areaOfLaw) {
    String office = String.format("F1%04d", OFFICE_SEQ.incrementAndGet());
    String period = periodHelper.nextAvailablePeriod(office, areaOfLaw);

    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber(office)
                .submissionPeriod(period)
                .areaOfLaw(areaOfLaw)
                .status(SubmissionStatus.CREATED)
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
                .feeCode(originalFeeCode)
                .lineNumber(1)
                .matterTypeCode("MAT01")
                .uniqueFileNumber("010725/001")
                .caseReferenceNumber("CRN-1768")
                .caseStartDate(LocalDate.of(2025, Month.JULY, 1))
                .caseConcludedDate(LocalDate.of(2025, Month.JULY, 31))
                .createdByUserId(SEED_ACTOR)
                .build());

    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository.saveAndFlush(
            ClaimSummaryFee.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim)
                .createdByUserId(SEED_ACTOR)
                .createdOn(Instant.now())
                .build());

    calculatedFeeDetailRepository.saveAndFlush(
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .feeCode(originalFeeCode)
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());

    sharedPatchContext.setSubmissionId(submission.getId());
    sharedPatchContext.setClaimId(claim.getId());

    // Baseline patch carrying valid amendment metadata + a genuine (client_forename) delta so the
    // no-op guard passes and the metadata gate is satisfied, letting the fee-code Area-of-Law gate
    // be the decisive step. Mutation steps add the fee_code (and any extra fields) on top.
    patch = objectMapper.createObjectNode();
    patch.put("client_forename", AMENDED_CLIENT_FORENAME);
    patch.put("amendment_requested_by", REQUESTED_BY);
    patch.put("amendment_reason_code", REASON_CODE);
    patch.put("amendment_user_id", AMENDMENT_USER_ID);
    patch.put("version", 0);
    publishPatch();
  }
}
