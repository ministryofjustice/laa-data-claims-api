package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code amendmentsEndpointOrchestration.feature} (DSTEW-1771).
 *
 * <p>Owns the cross-cutting end-to-end orchestration glue: a fully-valid Legal Help seed, the
 * orchestration amend composers (non-pricing, pricing, slow, fail-at-step, stub-selector header)
 * and the observable-proxy assertions. The generic outcome assertions (status, PDA/FSP call counts,
 * is_amended, version, claim_amendment row, CFD count, no-commit) are reused from sibling amendment
 * step classes. This class builds a COMPLETE valid patch itself (metadata + version) so it does not
 * depend on another class's patch-context wiring.
 */
public class AmendmentOrchestrationSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1771";
  private static final String VALID_REQUESTED_BY = "PROVIDER";
  private static final String VALID_REASON = "PROVIDER_ERROR";
  private static final String VALID_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-177100000001";
  private static final String WIRE_FEE_CODE = "ORCH1";
  private static final String CROSS_AOL_FEE_CODE = "ORCH2";
  private static final String TARGET_UCN = "01011990/A/BCDE";
  private static final String NON_AMENDABLE_FIELD = "representation_order_date";
  private static final Duration SLOW_BUT_OK = Duration.ofMillis(250);

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private BddMockServerSupport mock;
  @Autowired private AmendableClaimFixture fixture;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  @Autowired
  private uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport api;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ObjectNode patch;
  private Map<String, String> extraHeaders = Map.of();

  // ---------------------------------------------------------------------------
  // Given — seed + amend composers
  // ---------------------------------------------------------------------------

  @Given("an orchestration claim exists with area of law {string}")
  public void anOrchestrationClaimExists(String areaOfLaw) {
    AreaOfLaw area = AreaOfLaw.valueOf(areaOfLaw.trim());
    AmendableClaimFixture.Seeded seeded = fixture.legalHelpValid().withAreaOfLaw(area).seed();
    sharedPatchContext.setSubmissionId(seeded.submissionId());
    sharedPatchContext.setClaimId(seeded.claimId());
    sharedPatchContext.setBaselineClaimVersion(seeded.baselineVersion());
    sharedPatchContext.setBaselineCfdCount(countCfd(seeded.claimId()));
    patch = baseValidPatch();
  }

  @Given("an orchestration amendment updates only the field {string} to {string}")
  public void anOrchestrationAmendmentUpdatesField(String field, String value) {
    patch.put(field, value);
    publish();
  }

  @Given("an orchestration pricing amendment changes the fee code within the same area of law")
  public void anOrchestrationPricingAmendmentSameAreaOfLaw() throws IOException {
    // fee-details resolves the SAME area of law so the terminal fee-code gate passes; PDA + FSP are
    // stubbed OK so the pricing path runs an outbound PDA (/schedules) and FSP (/fee-calculation).
    mock.stubFeeDetailsAreaOfLaw("LEGAL_HELP");
    mock.stubProviderSchedulesOk();
    mock.stubAmendmentFspOk();
    patch.put("fee_code", WIRE_FEE_CODE);
    publish();
  }

  @Given(
      "an orchestration pricing amendment changes the fee code with slow-but-successful PDA and FSP")
  public void anOrchestrationPricingAmendmentSlow() throws IOException {
    // A slow-but-within-budget PDA dependency still completes: proves no Claims-API hard limit.
    mock.stubFeeDetailsAreaOfLaw("LEGAL_HELP");
    mock.stubProviderSchedulesWithDelay(SLOW_BUT_OK);
    mock.stubAmendmentFspOk();
    patch.put("fee_code", WIRE_FEE_CODE);
    publish();
  }

  @Given("the amendment request carries a DSTEW-1743 stub selector header {string}")
  public void theAmendmentRequestCarriesStubHeader(String selector) {
    extraHeaders = Map.of("X-Stub-Response", selector);
  }

  @io.cucumber.java.en.When(
      "I submit the amendment with the stub selector header and wait for amendment validation")
  public void iSubmitWithStubSelectorHeader() {
    api.patchClaimAmendment(
        sharedPatchContext.getSubmissionId(),
        sharedPatchContext.getClaimId(),
        sharedPatchContext.getPatchJson(),
        extraHeaders);
  }

  @Given("the orchestration amendment is set up to fail at step {string}")
  public void theOrchestrationAmendmentIsSetUpToFailAtStep(String failingStep) throws IOException {
    switch (failingStep.trim()) {
      case "request boundary (missing body)" -> sharedPatchContext.setPatchJson("");
      case "version contract (DSTEW-1751)" -> {
        patch.put("client_surname", "Jones");
        patch.put("version", 99);
        publish();
      }
      case "metadata (DSTEW-1765)" -> {
        patch.put("amendment_requested_by", "RB_NOT_A_REAL_CODE");
        patch.put("client_surname", "Jones");
        publish();
      }
      case "amendability (DSTEW-1767)" -> {
        patch.put(NON_AMENDABLE_FIELD, "15/07/2025");
        publish();
      }
      case "duplicate (DSTEW-1769)" -> {
        seedCollidingSibling();
        patch.put("client_surname", "Jones");
        publish();
      }
      default -> throw new IllegalArgumentException("Unsupported failing step: " + failingStep);
    }
  }

  // ---------------------------------------------------------------------------
  // Then — observable-proxy assertions
  // ---------------------------------------------------------------------------

  @Then("exactly {int} outbound PDA call was made")
  public void exactlyNoutboundPdaCallsWereMade(int expected) {
    mock.verifyProviderSchedulesCalled(VerificationTimes.exactly(expected));
  }

  @Then("the amendment endpoint returns HTTP status {int}")
  public void theAmendmentEndpointReturnsHttpStatus(int expected) {
    assertThat(scenarioContext.getLastStatusCode())
        .as("amendment endpoint HTTP status (body=%s)", scenarioContext.getLastResponseBody())
        .isEqualTo(expected);
  }

  @Then("the orchestration amendment persisted the field {string} as {string}")
  public void theOrchestrationAmendmentPersistedField(String field, String expected) {
    // Only client_surname is asserted structurally here (the field the happy-path scenario amends);
    // the persisted value is read back from the Client row bound to the amended claim.
    if (!"client_surname".equals(field)) {
      throw new IllegalArgumentException("Unsupported persisted-field assertion: " + field);
    }
    Client client =
        clientRepository.findAll().stream()
            .filter(
                c ->
                    c.getClaim() != null
                        && sharedPatchContext.getClaimId().equals(c.getClaim().getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no Client row for amended claim"));
    assertThat(client.getClientSurname())
        .as("amended client_surname must be persisted")
        .isEqualTo(expected);
  }

  @Then("the response body is not the DSTEW-1743 stub error envelope")
  public void theResponseBodyIsNotTheStubEnvelope() {
    JsonNode body = scenarioContext.getLastResponseBody();
    // The retired DSTEW-1743 stub returned a 4xx multi-error envelope; a real success is a 2xx with
    // no error envelope. Prove the stub selector header did not steer us onto the stub path.
    if (body != null) {
      assertThat(body.path("errors").isArray() && body.path("errors").size() > 0)
          .as("real success response must not carry the stub error envelope (body=%s)", body)
          .isFalse();
    }
    Integer status = scenarioContext.getLastStatusCode();
    assertThat(status)
        .as("real endpoint must return a 2xx success")
        .isNotNull()
        .isBetween(200, 299);
  }

  @Then("the orchestration invoked the outbound PDA and FSP calls")
  public void theOrchestrationInvokedPdaAndFsp() {
    // The two observable outbound orchestration dependencies on the amendment pricing path. The
    // fee-code details lookup is resolved inside the reusable validation package (cached on the
    // ResolvedClaimData) rather than as a separate outbound HTTP call, so it is not asserted here.
    mock.verifyProviderSchedulesCalled(VerificationTimes.atLeast(1));
    mock.verifyAmendmentFspCalculationCalled(VerificationTimes.atLeast(1));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ObjectNode baseValidPatch() {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("amendment_requested_by", VALID_REQUESTED_BY);
    node.put("amendment_reason_code", VALID_REASON);
    node.put("amendment_user_id", VALID_USER_ID);
    node.put("version", 0);
    return node;
  }

  private void publish() {
    sharedPatchContext.setPatchJson(patch.toString());
  }

  private long countCfd(java.util.UUID claimId) {
    return calculatedFeeDetailRepository.findAll().stream()
        .filter(cfd -> cfd.getClaim() != null && claimId.equals(cfd.getClaim().getId()))
        .count();
  }

  private void seedCollidingSibling() {
    Claim target = claimRepository.findById(sharedPatchContext.getClaimId()).orElseThrow();
    Claim sibling =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(target.getSubmission())
                .status(ClaimStatus.VALID)
                .feeCode(target.getFeeCode())
                .lineNumber(2)
                .matterTypeCode(target.getMatterTypeCode())
                .scheduleReference(target.getScheduleReference())
                .uniqueFileNumber(target.getUniqueFileNumber())
                .caseReferenceNumber("CRN-1771-SIB")
                .caseStartDate(target.getCaseStartDate())
                .caseConcludedDate(target.getCaseConcludedDate())
                .createdByUserId(SEED_ACTOR)
                .build());
    clientRepository.saveAndFlush(
        Client.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(sibling)
            .clientForename("Jane")
            .clientSurname("Smith")
            .clientDateOfBirth(LocalDate.of(1990, Month.JANUARY, 1))
            .uniqueClientNumber(TARGET_UCN)
            .clientPostcode("SW1H 9HE")
            .genderCode("F")
            .ethnicityCode("99")
            .disabilityCode("COG")
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());
  }
}
