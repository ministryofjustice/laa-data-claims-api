package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Shared cucumber step glue owned by the DSTEW-2301 amendment BDD harness.
 *
 * <p>Provides the phrases every downstream amendment BDD story needs — seeding a fresh amendable
 * claim, arming the FSP + PDA mocks, submitting a well-formed amendment PATCH, and asserting
 * observable outcomes (accepted / rejected / no side effects / row counts). All step bodies are
 * wrapped in {@code BddStepFailures.step(...)} per the standing rule.
 *
 * <p><b>Scenario-scoped state</b>: seeded IDs and the baseline CFD count are recorded in
 * {@link SharedAmendmentPatchContext} so the reused {@code When I submit the amendment ...} step
 * (owned by {@code AmendmentMetadataValidationSteps}) can pick them up transparently.
 *
 * <p>Ticket: DSTEW-2301.
 */
@Slf4j
public class AmendmentHarnessCommonSteps {

  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-230100000001";

  @Autowired private AmendableClaimFixture fixture;
  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private FeeSchemePlatformRestClient feeSchemePlatformRestClient;
  @Autowired private ValidationService validationService;

  // Scenario-scoped bookkeeping. Instantiated fresh per scenario because cucumber-spring gives us
  // a new step-class instance per scenario when the class isn't @ScenarioScope.
  private long baselineCfdCount;
  private Long baselineClaimVersion;

  // ---------------------------------------------------------------------------
  // Given — seed the amendable claim
  // ---------------------------------------------------------------------------

  @Given("a fresh amendable claim on a legal-help submission at version {long}")
  public void aFreshAmendableClaimOnLegalHelpSubmissionAtVersion(long version) {
    step(
        "seed a fresh amendable Legal Help claim at version " + version,
        () -> {
          AmendableClaimFixture.Seeded seeded = fixture.legalHelpValid().withVersion(version).seed();
          sharedPatchContext.setSubmissionId(seeded.submissionId());
          sharedPatchContext.setClaimId(seeded.claimId());
          sharedPatchContext.setPatchJson(buildNonPricingPatch(seeded.baselineVersion()));
          baselineClaimVersion = seeded.baselineVersion();
          baselineCfdCount = countCfd(seeded.claimId());
        });
  }

  // ---------------------------------------------------------------------------
  // Given — arm the external-service mocks (baseline "happy" is default; these
  // steps exist so scenarios can be explicit + so failure-mode variants are
  // available as they land)
  // ---------------------------------------------------------------------------

  @Given("the PDA service will respond {string} within the amendment-path timeout")
  public void thePdaServiceWillRespondWithinTimeout(String outcome) {
    step(
        "arm PDA mock to respond \"" + outcome + "\"",
        () -> {
          // Default (empty ClaimValidationResult with valid=true, no issues) is already applied by
          // BddAmendmentResetHook — that IS an "authorised" outcome. Non-"authorised" arming lands
          // when DSTEW-1774 delivers PDA-specific scenarios; leave a spec-guard log for now.
          log.info("[DSTEW-2301] PDA mock outcome expected: {} (defaults already applied)", outcome);
        });
  }

  @Given("the FSP service will return a valid fee calculation for the amendment")
  public void theFspServiceWillReturnAValidFeeCalculation() {
    step(
        "arm FSP mock to return 200 OK with a baseline FeeCalculationResponse",
        () ->
            doReturn(ResponseEntity.ok(new FeeCalculationResponse()))
                .when(feeSchemePlatformRestClient)
                .calculateFee(any()));
  }

  @Given("the FSP service will fail with HTTP {int}")
  public void theFspServiceWillFailWithHttp(int status) {
    step(
        "arm FSP mock to throw WebClientResponseException with status " + status,
        () -> {
          WebClientResponseException ex =
              WebClientResponseException.create(status, "Simulated FSP failure", null, null, null);
          doThrow(ex).when(feeSchemePlatformRestClient).calculateFee(any());
        });
  }

  // ---------------------------------------------------------------------------
  // When — submit a well-formed non-pricing amendment
  // ---------------------------------------------------------------------------

  @When("I submit a well-formed non-pricing amendment")
  public void iSubmitAWellFormedNonPricingAmendment() {
    step(
        "PATCH the amendment endpoint with a well-formed non-pricing payload",
        () -> {
          if (!sharedPatchContext.isPopulated()) {
            throw new IllegalStateException(
                "No amendable claim seeded — call 'a fresh amendable claim ...' first");
          }
          api.patchClaimAmendment(
              sharedPatchContext.getSubmissionId(),
              sharedPatchContext.getClaimId(),
              sharedPatchContext.getPatchJson());
          log.info(
              "[DSTEW-2301] PATCH amendment for claim {} → status={} body={}",
              sharedPatchContext.getClaimId(),
              scenarioContext.getLastStatusCode(),
              scenarioContext.getLastResponseBody());
        });
  }

  // ---------------------------------------------------------------------------
  // Then — outcome assertions
  // ---------------------------------------------------------------------------

  @Then("the amendment is accepted")
  public void theAmendmentIsAccepted() {
    step(
        "assert the last PATCH returned a 2xx status",
        () -> {
          Integer status = scenarioContext.getLastStatusCode();
          assertThat(status)
              .as(
                  "Expected 2xx for a well-formed amendment (body=%s)",
                  scenarioContext.getLastResponseBody())
              .isNotNull()
              .satisfies(s -> assertThat(s / 100).isEqualTo(2));
        });
  }

  @Then("claim.version is now {long}")
  public void claimVersionIsNow(long expected) {
    step(
        "assert claim.version = " + expected,
        () -> {
          Claim claim = requireClaim();
          assertThat(claim.getVersion())
              .as("claim.version after amendment")
              .isEqualTo(expected);
        });
  }

  @Then("claim.is_amended is true")
  public void claimIsAmendedIsTrue() {
    step(
        "assert claim.is_amended = true",
        () -> assertThat(requireClaim().isAmended()).as("claim.is_amended").isTrue());
  }

  @Then("exactly one claim_amendment row was inserted for this claim")
  public void exactlyOneClaimAmendmentRowWasInsertedForThisClaim() {
    step(
        "assert exactly one claim_amendment row exists",
        () -> {
          List<ClaimAmendment> rows =
              claimAmendmentRepository.findByClaimIdOrderByIdDesc(sharedPatchContext.getClaimId());
          assertThat(rows)
              .as("claim_amendment rows for claim %s", sharedPatchContext.getClaimId())
              .hasSize(1);
        });
  }

  @Then("no claim_amendment record was inserted for this claim by this attempt")
  public void noClaimAmendmentRecordWasInsertedForThisClaimByThisAttempt() {
    step(
        "assert no claim_amendment row inserted",
        () -> {
          List<ClaimAmendment> rows =
              claimAmendmentRepository.findByClaimIdOrderByIdDesc(sharedPatchContext.getClaimId());
          assertThat(rows).as("claim_amendment rows").isEmpty();
        });
  }

  @Then("no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt")
  public void noFspDerivedCalculatedFeeDetailRowWasInsertedForThisClaimByThisAttempt() {
    step(
        "assert no new calculated_fee_detail row inserted",
        () -> {
          long now = countCfd(sharedPatchContext.getClaimId());
          assertThat(now)
              .as("calculated_fee_detail row count for claim %s", sharedPatchContext.getClaimId())
              .isEqualTo(baselineCfdCount);
        });
  }

  @Then("the claim persisted state matches the pre-amendment state")
  public void theClaimPersistedStateMatchesThePreAmendmentState() {
    step(
        "assert claim.version unchanged from baseline",
        () -> {
          Claim claim = requireClaim();
          assertThat(claim.getVersion())
              .as("claim.version must remain at %s (pre-amendment baseline)", baselineClaimVersion)
              .isEqualTo(baselineClaimVersion);
          assertThat(claim.isAmended()).as("claim.is_amended must remain false").isFalse();
        });
  }

  // ---------------------------------------------------------------------------
  // Then — outbound-call verification against the mocks
  // ---------------------------------------------------------------------------

  @Then("no outbound PDA call was made")
  public void noOutboundPdaCallWasMade() {
    step(
        "verify the mocked ValidationService was NOT invoked with the PDA validator",
        () ->
            verify(validationService, never())
                .validateClaim(any(), any(), any()));
  }

  @Then("no outbound FSP call was made")
  public void noOutboundFspCallWasMade() {
    step(
        "verify the mocked FSP client's calculateFee was NOT invoked",
        () -> verify(feeSchemePlatformRestClient, never()).calculateFee(any()));
  }

  @Then("exactly {int} outbound FSP call was made")
  public void exactlyNOutboundFspCallsWereMade(int expected) {
    step(
        "verify FSP.calculateFee was invoked exactly " + expected + " times",
        () -> verify(feeSchemePlatformRestClient, times(expected)).calculateFee(any()));
  }

  @Then("exactly {int} outbound PDA call was made")
  public void exactlyNOutboundPdaCallsWereMade(int expected) {
    step(
        "verify PDA-scoped validateClaim was invoked exactly " + expected + " times",
        () -> verify(validationService, times(expected)).validateClaim(any(), any(), any()));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Claim requireClaim() {
    UUID claimId = sharedPatchContext.getClaimId();
    return claimRepository
        .findById(claimId)
        .orElseThrow(
            () -> new AssertionError("Claim missing after PATCH: " + claimId));
  }

  private long countCfd(UUID claimId) {
    // Repository has no count method — pull the latest and use its presence as a proxy is wrong.
    // Instead, iterate the JpaRepository.findAll() would be too heavy; the amendment path only
    // ever appends CFD rows so counting via a single query is not exposed. Use the "latest" lookup
    // plus a full findAll filter as a fallback; acceptable for BDD scope.
    return calculatedFeeDetailRepository.findAll().stream()
        .map(CalculatedFeeDetail::getClaim)
        .filter(java.util.Objects::nonNull)
        .map(Claim::getId)
        .filter(claimId::equals)
        .count();
  }

  private String buildNonPricingPatch(long submittedVersion) {
    return "{\"version\":" + submittedVersion
        + ",\"amendment_requested_by\":\"PROVIDER\""
        + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
        + ",\"amendment_user_id\":\"" + AMENDMENT_USER_ID + "\""
        + ",\"client_forename\":\"Harness-Canary\"}";
  }
}



