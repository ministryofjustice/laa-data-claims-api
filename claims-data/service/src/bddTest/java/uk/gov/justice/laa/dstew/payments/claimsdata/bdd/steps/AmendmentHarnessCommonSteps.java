package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;

/**
 * Shared cucumber step glue owned by the DSTEW-2301 amendment BDD harness.
 *
 * <p>Provides the phrases every downstream amendment BDD story needs — seeding a fresh amendable
 * claim, arming the FSP + PDA mocks, submitting a well-formed amendment PATCH, and asserting
 * observable outcomes (accepted / rejected / no side effects / row counts). All step bodies are
 * wrapped in {@code BddStepFailures.step(...)} per the standing rule.
 *
 * <p><b>Scenario-scoped state</b>: seeded IDs and the baseline CFD count are recorded in {@link
 * SharedAmendmentPatchContext} so the reused {@code When I submit the amendment ...} step (owned by
 * {@code AmendmentMetadataValidationSteps}) can pick them up transparently.
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
  @Autowired private BddMockServerSupport mock;

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
          AmendableClaimFixture.Seeded seeded =
              fixture.legalHelpValid().withVersion(version).seed();
          sharedPatchContext.setSubmissionId(seeded.submissionId());
          sharedPatchContext.setClaimId(seeded.claimId());
          sharedPatchContext.setPatchJson(buildNonPricingPatch(seeded.baselineVersion()));
          baselineClaimVersion = seeded.baselineVersion();
          baselineCfdCount = countCfd(seeded.claimId());
          // Publish the baseline onto the scenario-scoped context too so baseline-relative Thens
          // work uniformly whether the claim was provisioned here or by another step class (e.g.
          // the DSTEW-1767 assessed-pricing provisioning step).
          sharedPatchContext.setBaselineClaimVersion(baselineClaimVersion);
          sharedPatchContext.setBaselineCfdCount(baselineCfdCount);
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
        "arm PDA MockServer /schedules stub to respond \"" + outcome + "\"",
        () -> {
          // Only the happy-path "authorised" outcome is implemented today. Non-"authorised" arming
          // (rejected / timeout) lands with DSTEW-1774. Failing fast here means a scenario that
          // asks for a non-happy outcome cannot silently pass against the default happy-path stub
          // and produce a false-positive green — it will error with a clear message that points
          // the reader at the follow-up ticket.
          if (!"authorised".equalsIgnoreCase(outcome)) {
            throw new UnsupportedOperationException(
                "PDA outcome \""
                    + outcome
                    + "\" is not yet armed by the harness — only \"authorised\" is implemented"
                    + " (default). Non-\"authorised\" variants land with DSTEW-1774. Failing fast"
                    + " to avoid a false-positive green against the happy-path default stub.");
          }
          // Since validateClaim converged onto the real ValidationService facade (DSTEW-2317), a
          // pricing amendment (PDA-impacting) now dispatches a REAL provider-details /schedules
          // call. Arm it with a 200 OK so the "authorised" outcome is exercised over real HTTP
          // rather than assumed via a Mockito stub.
          mock.stubProviderSchedulesOk();
          log.info("[DSTEW-2317] PDA /schedules stub armed: authorised (200 OK)");
        });
  }

  @Given("the FSP service will return a valid fee calculation for the amendment")
  public void theFspServiceWillReturnAValidFeeCalculation() {
    step(
        "arm FSP MockServer stub to return 200 OK for fee-details + fee-calculation",
        () -> mock.stubAmendmentFspOk());
  }

  @Given("the FSP service will fail with HTTP {int}")
  public void theFspServiceWillFailWithHttp(int status) {
    step(
        "arm FSP MockServer fee-calculation stub to return HTTP " + status,
        () -> mock.stubAmendmentFspCalculationStatus(status));
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

  @When("I submit a well-formed pricing amendment")
  public void iSubmitAWellFormedPricingAmendment() {
    step(
        "PATCH the amendment endpoint with a well-formed pricing (case_start_date) payload",
        () -> {
          if (!sharedPatchContext.isPopulated()) {
            throw new IllegalStateException(
                "No amendable claim seeded — call 'a fresh amendable claim ...' first");
          }
          // A pricing-impacting field change (case_start_date is on the FSP fee-calculation
          // request body — see FeeSchemeRequestField) drives AmendmentFspValidationStep to make
          // a real calculateFee call. Unlike a fee_code change it does not shift the resolved
          // area of law, so it isolates the repricing HTTP path from the AoL eligibility gate.
          sharedPatchContext.setPatchJson(buildPricingPatch(baselineClaimVersion));
          api.patchClaimAmendment(
              sharedPatchContext.getSubmissionId(),
              sharedPatchContext.getClaimId(),
              sharedPatchContext.getPatchJson());
          log.info(
              "[DSTEW-2353] PATCH pricing amendment for claim {} → status={} body={}",
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
          assertThat(claim.getVersion()).as("claim.version after amendment").isEqualTo(expected);
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

  @Then("the claim persisted state matches the pre-amendment state")
  public void theClaimPersistedStateMatchesThePreAmendmentState() {
    step(
        "assert claim.version unchanged from baseline",
        () -> {
          Long baseline = resolveBaselineClaimVersion();
          if (baseline == null) {
            baseline = requireClaim().getVersion();
          }
          Claim claim = requireClaim();
          assertThat(claim.getVersion())
              .as("claim.version must remain at %s (pre-amendment baseline)", baseline)
              .isEqualTo(baseline);
          assertThat(claim.isAmended()).as("claim.is_amended must remain false").isFalse();
        });
  }

  @Then("no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt")
  public void noFspDerivedCalculatedFeeDetailRowWasInsertedForThisClaimByThisAttempt() {
    step(
        "assert no new calculated_fee_detail row inserted",
        () -> {
          long baseline = resolveBaselineCfdCount();
          long now = countCfd(sharedPatchContext.getClaimId());
          assertThat(now)
              .as("calculated_fee_detail row count for claim %s", sharedPatchContext.getClaimId())
              .isEqualTo(baseline);
        });
  }

  // ---------------------------------------------------------------------------
  // Then — outbound-call verification against MockServer
  //
  // PDA suppression semantics (see AmendmentExternalValidationStep lines 78-81):
  //   * The amendment external-validation step drops the PDA validator code from
  //     the validator-set when the amendment does not impact PDA, so the real
  //     ValidationService facade makes NO outbound provider-details /schedules
  //     call; retaining it dispatches the call.
  //   * Now that validateClaim runs over real HTTP (DSTEW-2317) we observe this
  //     directly on the MockServer request journal — a recorded /schedules GET
  //     means the PDA read was dispatched, its absence means it was suppressed.
  //     Positive-path PDA assertions live in AmendmentPdaTriggerSteps /
  //     AmendmentPdaOutcomeMappingSteps via verifyProviderSchedulesCalled(...).
  // ---------------------------------------------------------------------------

  @Then("no outbound PDA call was made")
  public void noOutboundPdaCallWasMade() {
    step(
        "verify MockServer recorded no outbound PDA /schedules call — the amendment flow either"
            + " short-circuited before validateClaim (eligibility / retrieval / request-contract"
            + " gates) or reached the real ValidationService facade with a validator-set that omits"
            + " CLAIM_CATEGORY_OF_LAW_VALIDATOR; both express the same observable contract over real"
            + " HTTP now that validateClaim is transport-based (DSTEW-2317)",
        () -> mock.verifyProviderSchedulesCalled(VerificationTimes.never()));
  }

  // Renamed from "no outbound FSP call was made" to avoid DuplicateStepDefinitionException
  // with AmendmentsEligibilityGateSteps (DSTEW-1764, merged via PR #452). Both classes need a
  // no-FSP-call assertion but ours is a real MockServer request-count check on the FSP
  // fee-calculation endpoint, while the eligibility-gate one is a pure symbolic spec-guard. The
  // "(harness-verified)" qualifier makes the difference explicit at the feature-file level.
  @Then("no outbound FSP call was made from the amendment harness")
  public void noOutboundFspCallWasMade() {
    step(
        "verify no outbound FSP fee-calculation call was recorded by MockServer",
        () -> mock.verifyAmendmentFspCalculationCalled(VerificationTimes.never()));
  }

  @Then("exactly {int} outbound FSP call was made")
  public void exactlyNOutboundFspCallsWereMade(int expected) {
    step(
        "verify FSP fee-calculation was called exactly " + expected + " times",
        () -> mock.verifyAmendmentFspCalculationCalled(VerificationTimes.exactly(expected)));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Claim requireClaim() {
    UUID claimId = sharedPatchContext.getClaimId();
    return claimRepository
        .findById(claimId)
        .orElseThrow(() -> new AssertionError("Claim missing after PATCH: " + claimId));
  }

  /**
   * Resolves the pre-amendment claim version baseline: the scenario-scoped context wins (it may
   * have been recorded by a provisioning step in another step class, e.g. the DSTEW-1767
   * assessed-pricing provisioning), falling back to this class's own field when only the harness
   * seeded the claim.
   */
  private Long resolveBaselineClaimVersion() {
    return sharedPatchContext.getBaselineClaimVersion() != null
        ? sharedPatchContext.getBaselineClaimVersion()
        : baselineClaimVersion;
  }

  /**
   * Resolves the pre-amendment calculated_fee_detail row-count baseline. Shared context wins (see
   * {@link #resolveBaselineClaimVersion()}); falls back to this class's own field.
   */
  private long resolveBaselineCfdCount() {
    return sharedPatchContext.getBaselineCfdCount() != null
        ? sharedPatchContext.getBaselineCfdCount()
        : baselineCfdCount;
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
    return "{\"version\":"
        + submittedVersion
        + ",\"amendment_requested_by\":\"PROVIDER\""
        + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
        + ",\"amendment_user_id\":\""
        + AMENDMENT_USER_ID
        + "\""
        + ",\"client_forename\":\"Harness-Canary\"}";
  }

  private String buildPricingPatch(long submittedVersion) {
    // case_start_date is a pricing-impacting FSP request-body field; changing it from the
    // fixture's seeded 01/07/2025 triggers a single FeeSchemePlatformRestClient.calculateFee.
    return "{\"version\":"
        + submittedVersion
        + ",\"amendment_requested_by\":\"PROVIDER\""
        + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
        + ",\"amendment_user_id\":\""
        + AMENDMENT_USER_ID
        + "\""
        + ",\"case_start_date\":\"04/08/2025\"}";
  }
}
