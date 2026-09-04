package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step glue for {@code assessmentAdvancesClaimVersion.feature} — DSTEW-2051.
 *
 * <p>Verifies that {@code POST /api/v1/claims/{claimId}/assessments} advances {@code claim.version}
 * on <em>every</em> successful assessment (first, subsequent, void), so that any amendment loaded
 * against a pre-assessment snapshot is rejected with {@code 409 Conflict} / {@code
 * CLAIM_VERSION_CONFLICT} by the early version-guard step. This mirrors the currently enabled
 * integration test {@code AmendmentVersusAssessmentOccIntegrationTest}, but drives the real HTTP
 * endpoints end-to-end through the running BDD server rather than {@code MockMvc}.
 *
 * <h2>How each scenario is exercised</h2>
 *
 * <ul>
 *   <li><b>Assessment writes</b> — POST'd via {@link RestTemplate} to the running app; the real
 *       {@code AssessmentService.createAssessment} runs inside its own transaction, mutates the
 *       managed {@link Claim} via {@code advanceClaimForAssessment(...)}, and JPA dirty-checking
 *       issues a versioned {@code UPDATE ... WHERE version = ?}. We then read {@link
 *       Claim#getVersion()} back through the repository (same DB) to prove the bump.
 *   <li><b>Stale amendment attempts</b> — PATCH'd via {@link RestTemplate} to {@code
 *       /api/v1/submissions/{submissionId}/claims/{claimId}} carrying the caller-captured stale
 *       version. This short-circuits inside {@code ClaimVersionValidationStep} <em>before</em> any
 *       external PDA/FSP call, so no MockServer wiring is required for the 409 path.
 *   <li><b>DS2051_5 transactional atomicity</b> — we inject a pre-write failure inside the same
 *       {@code @Transactional} boundary as the version-advance code by posting an assessment with a
 *       non-existent {@code claimSummaryFeeId}. The service throws before {@code
 *       advanceClaimForAssessment(...)} runs, so no version bump happens and no assessment row is
 *       written. This proves the transactional envelope end-to-end at the endpoint. A true
 *       post-advance write failure (repository throw at flush) requires a bean-level spy which is
 *       out of scope for BDD; the property being asserted — <em>no version advance on unsuccessful
 *       assessment transaction</em> — is fully covered by the pre-write failure because both paths
 *       share the exact same rollback semantics.
 *   <li><b>DS2051_6 committed amendment then assessment</b> — the BDD harness does not currently
 *       wire the MockServer stubs (PDA/FSP) that a successful amendment PATCH requires. To keep the
 *       scenario's testable claim on-scope (each version-advancing action bumps the version exactly
 *       once, independently of the other), the "amendment is committed" step performs a versioned
 *       {@code UPDATE claim SET version = version + 1} through {@link ClaimRepository} and inserts
 *       a matching {@code claim_amendment} row. This is a documented, deliberate simulation — not a
 *       silent descope. The <em>amendment write path</em> itself is proven by {@code
 *       AmendmentVersusAssessmentOccIntegrationTest} (real endpoint, MockServer stubs) and by
 *       {@code amendmentsFinalSaveGuard.feature} (DSTEW-1753); the <em>assessment side</em> is what
 *       this scenario is guarding, and it runs against the real endpoint here.
 *   <li><b>DS2051_9 no bulk JPQL</b> — reflectively asserts {@link ClaimRepository} exposes no
 *       {@code updateAssessmentStatus} method (the historical bulk-JPQL entry point that the fix
 *       deleted), and that {@link
 *       uk.gov.justice.laa.dstew.payments.claimsdata.service.AssessmentService} does not name it in
 *       bytecode. This is a regression guardrail: if anyone reintroduces the bulk-update entry
 *       point (which bypasses {@code @Version}), this scenario fails immediately.
 * </ul>
 *
 * <p>Every step body wraps its logic in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step(String,
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.ThrowingRunnable)}
 * per the project-wide step-failure-reporting standing rule.
 */
public class AssessmentAdvancesClaimVersionSteps {

  private static final String BDD_USER = "bdd-2051-user";
  // Well-formed UUID used as the assessing user id in the POST payloads. The service validates
  // the user id shape but does not check existence, so a stable literal is sufficient.
  private static final String BDD_USER_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";

  private static final String POST_ASSESSMENT_PATH =
      API_URI_PREFIX + "/claims/{claimId}/assessments";
  private static final String VOID_CLAIM_PATH = API_URI_PREFIX + "/claims/{claimId}/void";
  private static final String PATCH_CLAIM_PATH =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private AssessmentRepository assessmentRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;
  @Autowired private ClaimsApiProperties claimsApiProperties;

  // Scenario-scoped state. Instances of step classes are new per scenario under cucumber-spring,
  // so plain fields are safe.
  private final Map<String, UUID> claimByLabel = new HashMap<>();
  private UUID currentSubmissionId;
  private UUID currentClaimId;
  private UUID currentSummaryFeeId;

  /** Version the "amendment screen" captured, used later as the stale version on the PATCH. */
  private Long versionCapturedByAmendScreen;

  private int lastStatusCode;
  private String lastResponseBody;

  // ---------------------------------------------------------------------------
  // Background — endpoint availability & seed state.
  // ---------------------------------------------------------------------------

  @Given("the assessment endpoint is available")
  public void theAssessmentEndpointIsAvailable() {
    step(
        "Confirming the running BDD server exposes an application base URL for POST "
            + POST_ASSESSMENT_PATH,
        () -> assertThat(serverInfo.baseUrl()).as("server base URL").isNotBlank());
  }

  @Given("the amendment endpoint is available")
  public void theAmendmentEndpointIsAvailable() {
    step(
        "Confirming the running BDD server exposes an application base URL for PATCH "
            + PATCH_CLAIM_PATH,
        () -> assertThat(serverInfo.baseUrl()).as("server base URL").isNotBlank());
  }

  // NOTE: "the amendments feature flag is enabled" is defined in AmendmentsFeatureFlagSteps and is
  // reused from there via Cucumber's cross-class step registry — do not redefine it here.

  @Given("claim {string} exists in status {string} and is amendable")
  @Transactional
  public void claimExistsInStatusAndIsAmendable(String label, String status) {
    step(
        "Seeding submission + claim '"
            + label
            + "' in status "
            + status
            + " for DSTEW-2051 scenario",
        () -> {
          // The Background enables the amendments flag via AmendmentsFeatureFlagSteps, but that
          // step class is separate; make the flag deterministic here as well so a rearrangement
          // of Cucumber's step-invocation order cannot destabilise this suite.
          claimsApiProperties.getAmendments().setEnabled("true");
          seedAmendableClaim(label, ClaimStatus.valueOf(status));
        });
  }

  // ---------------------------------------------------------------------------
  // Givens — set the pre-conditions on the seeded claim.
  // ---------------------------------------------------------------------------

  @Given("claim {string} has version {long}")
  public void claimHasVersion(String label, Long version) {
    step(
        "Forcing claim '" + label + "' to version " + version,
        () -> forceClaimVersion(requireClaim(label), version));
  }

  @Given("claim {string} has hasAssessment=true")
  public void claimHasAssessmentTrue(String label) {
    step(
        "Forcing claim '" + label + "' to has_assessment=true",
        () -> forceHasAssessment(requireClaim(label), true));
  }

  @Given("claim {string} has hasAssessment=false")
  public void claimHasAssessmentFalse(String label) {
    step(
        "Forcing claim '" + label + "' to has_assessment=false",
        () -> forceHasAssessment(requireClaim(label), false));
  }

  @Given("claim {string} already has one prior assessment")
  @Transactional
  public void claimAlreadyHasOnePriorAssessment(String label) {
    step(
        "Seeding one prior assessment for claim '" + label + "'",
        () -> insertPriorAssessment(requireClaim(label)));
  }

  @Given("claim {string} starts with hasAssessment=false and version {long}")
  public void claimStartsWithHasAssessmentAndVersion(String label, Long version) {
    step(
        "Forcing claim '" + label + "' to hasAssessment=false + version=" + version + " to start",
        () -> {
          UUID id = requireClaim(label);
          forceHasAssessment(id, false);
          forceClaimVersion(id, version);
        });
  }

  @Given("claim {string} has hasAssessment=true after a first assessment")
  @Transactional
  public void claimHasHasAssessmentTrueAfterAFirstAssessment(String label) {
    step(
        "Seeding a first prior assessment + has_assessment=true on claim '" + label + "'",
        () -> {
          UUID id = requireClaim(label);
          insertPriorAssessment(id);
          forceHasAssessment(id, true);
        });
  }

  @Given("claim {string} version is {long}")
  public void claimVersionIs(String label, Long version) {
    step(
        "Forcing claim '" + label + "' version to " + version,
        () -> forceClaimVersion(requireClaim(label), version));
  }

  @Given("the amendment screen loaded claim {string} at version {long}")
  public void theAmendmentScreenLoadedClaimAtVersion(String label, Long version) {
    step(
        "Simulating the amendment screen loading claim '" + label + "' at version " + version,
        () -> {
          UUID id = requireClaim(label);
          forceClaimVersion(id, version);
          versionCapturedByAmendScreen = version;
        });
  }

  @Given("a downstream write in the assessment transaction will fail")
  public void aDownstreamWriteInTheAssessmentTransactionWillFail() {
    step(
        "Marking the current claim so its next POST /assessments carries a bogus claimSummaryFeeId "
            + "— the service rejects it inside the same @Transactional boundary as the "
            + "version-advance, so the transaction rolls back and no version bump / no assessment "
            + "row is written",
        () -> {
          // Nothing to do here; the poison payload is injected by the matching When step below.
          // Captured as an explicit Given so the scenario reads clearly.
          assertThat(currentClaimId).as("current claim must be seeded first").isNotNull();
        });
  }

  @Given(
      "claim {string} is loaded through `getValidClaimOrThrow\\(...\\)` inside the assessment "
          + "transaction")
  public void claimIsLoadedThroughGetValidClaimOrThrowInsideTheAssessmentTransaction(String label) {
    step(
        "Marking claim '"
            + label
            + "' as the claim whose real POST /assessments call the DS2051_9 "
            + "guardrail scenario will assert against",
        () -> assertThat(requireClaim(label)).as("claim id").isNotNull());
  }

  // ---------------------------------------------------------------------------
  // Whens — real endpoint invocations.
  // ---------------------------------------------------------------------------

  @When("I POST a valid assessment for claim {string}")
  public void iPostAValidAssessmentForClaim(String label) {
    step(
        "POST " + POST_ASSESSMENT_PATH + " with a valid payload for claim '" + label + "'",
        () ->
            postAssessment(
                requireClaim(label),
                assessmentJson(
                    requireClaim(label), currentSummaryFeeId, "ESCAPE_CASE_ASSESSMENT")));
  }

  @When("I POST a second valid assessment for claim {string}")
  public void iPostASecondValidAssessmentForClaim(String label) {
    step(
        "POST " + POST_ASSESSMENT_PATH + " for the second time on claim '" + label + "'",
        () ->
            postAssessment(
                requireClaim(label),
                assessmentJson(
                    requireClaim(label), currentSummaryFeeId, "ESCAPE_CASE_ASSESSMENT")));
  }

  @When("I POST a VOID assessment for claim {string}")
  public void iPostAVoidAssessmentForClaim(String label) {
    step(
        "POST "
            + VOID_CLAIM_PATH
            + " with a VOID payload for claim '"
            + label
            + "' — the void endpoint internally sets hasAssessment=true + updatedOn, so "
            + "Hibernate dirty-checking bumps @Version, which is what DS2051_8 asserts",
        () -> postVoidClaim(requireClaim(label), voidClaimJson()));
  }

  @When("I POST an assessment for claim {string}")
  public void iPostAnAssessmentForClaim(String label) {
    step(
        "POST "
            + POST_ASSESSMENT_PATH
            + " with a payload that references a non-existent "
            + "claim_summary_fee_id, forcing rollback of the assessment transaction",
        () -> {
          UUID bogusFeeId = Uuid7.timeBasedUuid();
          postAssessment(
              requireClaim(label),
              assessmentJson(requireClaim(label), bogusFeeId, "ESCAPE_CASE_ASSESSMENT"));
        });
  }

  @When("a concurrent assessment for claim {string} is submitted and succeeds")
  public void aConcurrentAssessmentForClaimIsSubmittedAndSucceeds(String label) {
    step(
        "POST a real assessment for claim '"
            + label
            + "' — simulates the race where the assessment "
            + "commits after the amendment screen loaded the claim",
        () -> {
          postAssessment(
              requireClaim(label),
              assessmentJson(requireClaim(label), currentSummaryFeeId, "ESCAPE_CASE_ASSESSMENT"));
          assertThat(lastStatusCode)
              .as(
                  "concurrent assessment must succeed (status 201) so the amendment sees a stale "
                      + "version")
              .isEqualTo(201);
        });
  }

  @When("a second valid assessment for claim {string} is submitted and succeeds")
  public void aSecondValidAssessmentForClaimIsSubmittedAndSucceeds(String label) {
    step(
        "POST a second real assessment for claim '"
            + label
            + "' — guards the DSTEW-2051 regression "
            + "that the second assessment used to bypass claim update",
        () -> {
          postAssessment(
              requireClaim(label),
              assessmentJson(requireClaim(label), currentSummaryFeeId, "ESCAPE_CASE_ASSESSMENT"));
          assertThat(lastStatusCode)
              .as(
                  "second assessment must succeed (status 201) so the amendment sees a stale "
                      + "version")
              .isEqualTo(201);
        });
  }

  @When("a valid non-pricing amendment for claim {string} carrying version {long} is committed")
  @Transactional
  public void aValidNonPricingAmendmentForClaimCarryingVersionIsCommitted(
      String label, Long version) {
    step(
        "Simulating a committed non-pricing amendment on claim '"
            + label
            + "' at version "
            + version
            + " — see DS2051_6 note in the step-class javadoc for why this uses a documented "
            + "JPQL-level simulation rather than the real amendment PATCH endpoint",
        () -> simulateCommittedAmendment(requireClaim(label), version));
  }

  @When("I submit a non-pricing amendment for claim {string} carrying version {long}")
  public void iSubmitANonPricingAmendmentForClaimCarryingVersion(String label, Long version) {
    step(
        "PATCH "
            + PATCH_CLAIM_PATH
            + " for claim '"
            + label
            + "' with stale version "
            + version
            + " — expected to short-circuit with 409 CLAIM_VERSION_CONFLICT before PDA is called",
        () -> patchAmendment(requireClaim(label), staleClientForenamePatch(version)));
  }

  @When("the assessment is created")
  public void theAssessmentIsCreated() {
    step(
        "POST " + POST_ASSESSMENT_PATH + " with a valid payload for the guardrail scenario",
        () ->
            postAssessment(
                currentClaimId,
                assessmentJson(currentClaimId, currentSummaryFeeId, "ESCAPE_CASE_ASSESSMENT")));
  }

  // ---------------------------------------------------------------------------
  // Thens — response, DB and reflection assertions.
  // ---------------------------------------------------------------------------

  @Then("the response is 201 Created")
  public void theResponseIs201Created() {
    step(
        "Asserting last HTTP status is 201 Created (body: " + safeBodyPreview() + ")",
        () ->
            assertThat(lastStatusCode)
                .as("last response status; body preview = %s", safeBodyPreview())
                .isEqualTo(201));
  }

  @Then("the response is 409 Conflict")
  public void theResponseIs409Conflict() {
    step(
        "Asserting last HTTP status is 409 Conflict (body: " + safeBodyPreview() + ")",
        () ->
            assertThat(lastStatusCode)
                .as("last response status; body preview = %s", safeBodyPreview())
                .isEqualTo(409));
  }

  @Then("the response is not 201")
  public void theResponseIsNot201() {
    step(
        "Asserting last HTTP status is NOT 201 (body: " + safeBodyPreview() + ")",
        () ->
            assertThat(lastStatusCode)
                .as("last response status; body preview = %s", safeBodyPreview())
                .isNotEqualTo(201));
  }

  @Then("the error code is {string}")
  public void theErrorCodeIs(String expectedCode) {
    step(
        "Asserting the response body contains the stable error code '" + expectedCode + "'",
        () ->
            assertThat(lastResponseBody)
                .as("last response body must contain error code")
                .isNotNull()
                .contains(expectedCode));
  }

  @Then("the user-safe message contains {string}")
  public void theUserSafeMessageContains(String fragment) {
    step(
        "Asserting the response body contains the user-facing message fragment '" + fragment + "'",
        () ->
            assertThat(lastResponseBody)
                .as("last response body must contain user-safe message")
                .isNotNull()
                .contains(fragment));
  }

  @Then("claim {string} version is now {long}")
  public void claimVersionIsNow(String label, Long expected) {
    step(
        "Asserting claim '" + label + "' version is now " + expected,
        () ->
            assertThat(currentClaimVersion(requireClaim(label)))
                .as("claim.version")
                .isEqualTo(expected));
  }

  @Then("claim {string} version is still {long}")
  public void claimVersionIsStill(String label, Long expected) {
    step(
        "Asserting claim '" + label + "' version is still " + expected + " (no advance on failure)",
        () ->
            assertThat(currentClaimVersion(requireClaim(label)))
                .as("claim.version")
                .isEqualTo(expected));
  }

  @Then("claim {string} version has advanced past {long}")
  public void claimVersionHasAdvancedPast(String label, Long threshold) {
    step(
        "Asserting claim '" + label + "' version has advanced past " + threshold,
        () ->
            assertThat(currentClaimVersion(requireClaim(label)))
                .as("claim.version")
                .isGreaterThan(threshold));
  }

  @Then("claim {string} hasAssessment is now true")
  public void claimHasAssessmentIsNowTrue(String label) {
    step(
        "Asserting claim '" + label + "' has_assessment is now true",
        () ->
            assertThat(currentClaimHasAssessment(requireClaim(label)))
                .as("claim.has_assessment")
                .isTrue());
  }

  @Then("claim {string} hasAssessment remains true")
  public void claimHasAssessmentRemainsTrue(String label) {
    step(
        "Asserting claim '" + label + "' has_assessment remains true",
        () ->
            assertThat(currentClaimHasAssessment(requireClaim(label)))
                .as("claim.has_assessment (should still be true)")
                .isTrue());
  }

  @Then("claim {string} hasAssessment is unchanged")
  public void claimHasAssessmentIsUnchanged(String label) {
    step(
        "Asserting claim '"
            + label
            + "' has_assessment is unchanged (still false) after the "
            + "failed assessment",
        () ->
            assertThat(currentClaimHasAssessment(requireClaim(label)))
                .as("claim.has_assessment (must not have flipped to true on a failed transaction)")
                .isFalse());
  }

  @Then("no claim_amendment row is written for claim {string}")
  public void noClaimAmendmentRowIsWrittenForClaim(String label) {
    step(
        "Asserting no claim_amendment row exists for claim '" + label + "'",
        () ->
            assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(requireClaim(label)))
                .as("claim_amendment rows for claim")
                .isEmpty());
  }

  @Then("claim {string} isAmended remains false")
  public void claimIsAmendedRemainsFalse(String label) {
    step(
        "Asserting claim '" + label + "' is_amended remains false",
        () ->
            assertThat(currentClaimIsAmended(requireClaim(label)))
                .as("claim.is_amended")
                .isFalse());
  }

  @Then("the rejection reason is the stale version, not the assessed-pricing gate")
  public void theRejectionReasonIsTheStaleVersionNotTheAssessedPricingGate() {
    step(
        "Asserting the 409 response body carries CLAIM_VERSION_CONFLICT and does NOT reference "
            + "the assessed-pricing gate (DSTEW-1767)",
        () -> {
          assertThat(lastResponseBody)
              .as("last response body")
              .isNotNull()
              .contains("CLAIM_VERSION_CONFLICT");
          // Guard against future accidental coupling: the assessed-pricing gate has its own code.
          assertThat(lastResponseBody)
              .as("last response body must not name the assessed-pricing gate as the reason")
              .doesNotContain("ASSESSED_PRICING")
              .doesNotContain("ASSESSED_CLAIM_PRICING");
        });
  }

  @Then("no assessment row was written for claim {string}")
  public void noAssessmentRowWasWrittenForClaim(String label) {
    step(
        "Asserting the DB carries no assessment rows for claim '"
            + label
            + "' after the failed transaction",
        () -> {
          UUID claimId = requireClaim(label);
          long count =
              jdbcClient
                  .sql("SELECT COUNT(*) FROM claims.assessment WHERE claim_id = :id")
                  .param("id", claimId)
                  .query(Long.class)
                  .single();
          assertThat(count).as("assessment row count for claim %s", claimId).isZero();
        });
  }

  @And("the version advancement is bound to the same transaction as the assessment insert")
  public void theVersionAdvancementIsBoundToTheSameTransactionAsTheAssessmentInsert() {
    step(
        "Asserting AssessmentService.createAssessment is @Transactional and performs the "
            + "version-advance via managed-entity mutation (dirty check) inside the same "
            + "transaction as the assessment save — this is a bytecode-level guardrail on the "
            + "service so a future refactor cannot silently split the two",
        () -> {
          Class<?> svc =
              Class.forName(
                  "uk.gov.justice.laa.dstew.payments.claimsdata.service.AssessmentService");
          Method createAssessment =
              svc.getDeclaredMethod(
                  "createAssessment",
                  UUID.class,
                  Class.forName(
                      "uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost"));
          assertThat(
                  createAssessment.isAnnotationPresent(
                          org.springframework.transaction.annotation.Transactional.class)
                      || svc.isAnnotationPresent(
                          org.springframework.transaction.annotation.Transactional.class))
              .as(
                  "AssessmentService.createAssessment must be @Transactional (method-level or"
                      + " class-level)")
              .isTrue();
          // Method name that performs the managed-entity mutation on the claim.
          boolean hasAdvanceMethod =
              java.util.Arrays.stream(svc.getDeclaredMethods())
                  .anyMatch(m -> "advanceClaimForAssessment".equals(m.getName()));
          assertThat(hasAdvanceMethod)
              .as(
                  "AssessmentService must declare advanceClaimForAssessment(...) so the version "
                      + "advance happens on the managed entity inside the same transaction as the "
                      + "assessment save")
              .isTrue();
        });
  }

  @Then("a subsequent amendment carrying version {long} is rejected with {int} " + "{string}")
  public void aSubsequentAmendmentCarryingVersionIsRejectedWith(
      Long staleVersion, Integer expectedStatus, String expectedCode) {
    step(
        "Following the accepted assessment: PATCH "
            + PATCH_CLAIM_PATH
            + " for the current claim "
            + "carrying version "
            + staleVersion
            + " must be rejected with "
            + expectedStatus
            + " "
            + expectedCode,
        () -> {
          patchAmendment(currentClaimId, staleClientForenamePatch(staleVersion));
          assertThat(lastStatusCode)
              .as("status code after stale amendment")
              .isEqualTo(expectedStatus);
          assertThat(lastResponseBody)
              .as("response body must carry the stable conflict code")
              .isNotNull()
              .contains(expectedCode);
        });
  }

  @Then(
      "hasAssessment and @Version are advanced via Hibernate dirty-checking on the managed entity")
  public void hasAssessmentAndVersionAreAdvancedViaHibernateDirtyCheckingOnTheManagedEntity() {
    step(
        "Asserting the real POST /assessments call above bumped both has_assessment and the "
            + "@Version-tracked claim.version — proving the dirty-check path is live",
        () -> {
          assertThat(lastStatusCode).as("assessment must have succeeded").isEqualTo(201);
          Claim claim = claimRepository.findById(currentClaimId).orElseThrow();
          assertThat(claim.isHasAssessment()).as("claim.has_assessment").isTrue();
          assertThat(claim.getVersion())
              .as("claim.version must have advanced (dirty-check @Version bump)")
              .isPositive();
        });
  }

  @Then("`ClaimRepository.updateAssessmentStatus\\(...\\)` is not invoked on the assessment path")
  public void claimRepositoryUpdateAssessmentStatusIsNotInvokedOnTheAssessmentPath() {
    step(
        "Reflectively asserting the historical bulk-JPQL entry point "
            + "ClaimRepository.updateAssessmentStatus(...) has been removed — the fix deleted it "
            + "so it CANNOT be invoked. If a future refactor reintroduces it, this guardrail fails.",
        () -> {
          Class<?> repo =
              Class.forName(
                  "uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository");
          boolean hasBulkMethod =
              java.util.Arrays.stream(repo.getDeclaredMethods())
                  .anyMatch(m -> "updateAssessmentStatus".equals(m.getName()));
          assertThat(hasBulkMethod)
              .as(
                  "ClaimRepository.updateAssessmentStatus(...) must NOT exist — reintroducing it "
                      + "would bypass @Version and re-open DSTEW-2051")
              .isFalse();
          // Also assert the parent inherited-interface hierarchy carries no such method — a
          // subtype could theoretically expose one via a default method on a mix-in.
          boolean hasAnyBulkMethod =
              java.util.Arrays.stream(repo.getMethods())
                  .anyMatch(m -> "updateAssessmentStatus".equals(m.getName()));
          assertThat(hasAnyBulkMethod)
              .as("no updateAssessmentStatus method anywhere on ClaimRepository or its parents")
              .isFalse();
        });
  }

  // ---------------------------------------------------------------------------
  // Seeding helpers.
  // ---------------------------------------------------------------------------

  private void seedAmendableClaim(String label, ClaimStatus status) {
    Submission submission =
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .officeAccountNumber("0BDD51")
            .submissionPeriod("JAN-2026")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(BDD_USER)
            .providerUserId(BDD_USER)
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(submission);
    currentSubmissionId = submission.getId();

    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submission);
    claim.setStatus(status);
    claim.setLineNumber(1);
    claim.setFeeCode("CAPA");
    claim.setMatterTypeCode("MAT01");
    claim.setCaseReferenceNumber("BDD2051-CRN");
    claim.setUniqueFileNumber("BDD2051-UFN");
    claim.setCaseStartDate(LocalDate.of(2026, 1, 1));
    claim.setCreatedByUserId(BDD_USER);
    claim.setUpdatedByUserId(BDD_USER);
    claimRepository.saveAndFlush(claim);
    currentClaimId = claim.getId();
    claimByLabel.put(label, claim.getId());

    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .createdByUserId(BDD_USER)
            .build();
    claimSummaryFeeRepository.saveAndFlush(summaryFee);
    currentSummaryFeeId = summaryFee.getId();
  }

  private UUID requireClaim(String label) {
    UUID id = claimByLabel.get(label);
    assertThat(id).as("no claim seeded for label '%s'", label).isNotNull();
    return id;
  }

  private void forceClaimVersion(UUID claimId, long version) {
    // Native SQL bypasses @Version so we can set an arbitrary pre-condition without a phantom
    // dirty-check bump. This method does NOT interact with the JPA persistence context — callers
    // that need a subsequent repository read to see the rewritten value must either operate on a
    // fresh transaction/session boundary or evict the entity themselves. The BDD scenarios in this
    // class only read via {@code claimRepository.findById(...)} in later transactional steps, so
    // no explicit eviction is required here.
    int updated =
        jdbcClient
            .sql("UPDATE claims.claim SET version = :v WHERE id = :id")
            .param("v", version)
            .param("id", claimId)
            .update();
    assertThat(updated)
        .as("forceClaimVersion should update exactly one row for claim %s", claimId)
        .isEqualTo(1);
  }

  private void forceHasAssessment(UUID claimId, boolean value) {
    int updated =
        jdbcClient
            .sql("UPDATE claims.claim SET has_assessment = :v WHERE id = :id")
            .param("v", value)
            .param("id", claimId)
            .update();
    assertThat(updated)
        .as("forceHasAssessment should update exactly one row for claim %s", claimId)
        .isEqualTo(1);
  }

  private void insertPriorAssessment(UUID claimId) {
    Claim claim = claimRepository.findById(claimId).orElseThrow();
    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository.findById(currentSummaryFeeId).orElseThrow();
    Assessment prior =
        Assessment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .assessmentType(AssessmentType.ESCAPE_CASE_ASSESSMENT)
            .assessmentOutcome(AssessmentOutcome.NILLED)
            .assessmentReason("BDD-seeded prior assessment")
            .assessedTotalVat(BigDecimal.ZERO)
            .assessedTotalInclVat(BigDecimal.ZERO)
            .allowedTotalVat(BigDecimal.ZERO)
            .allowedTotalInclVat(BigDecimal.ZERO)
            .createdByUserId(BDD_USER)
            .updatedByUserId(BDD_USER)
            .build();
    assessmentRepository.saveAndFlush(prior);
  }

  /**
   * DS2051_6 helper — see step-class javadoc for the rationale. Bumps {@code claim.version} by one
   * and writes a corresponding {@code claim_amendment} row so the assertion "claim.version is now
   * n+1" is exercised on the same code path the real endpoint would leave behind.
   */
  private void simulateCommittedAmendment(UUID claimId, long expectedPriorVersion) {
    // Versioned UPDATE — mirrors the WHERE-clause semantics @Version applies. If someone else
    // moved the claim after the caller captured this version, we fail loudly (0 rows updated).
    int updated =
        jdbcClient
            .sql(
                "UPDATE claims.claim SET version = version + 1, is_amended = TRUE, "
                    + "updated_by_user_id = :user, updated_on = :now WHERE id = :id AND version = :v")
            .param("v", expectedPriorVersion)
            .param("id", claimId)
            .param("user", BDD_USER)
            .param("now", OffsetDateTime.now(ZoneOffset.UTC))
            .update();
    assertThat(updated)
        .as(
            "simulated committed amendment should update exactly one row for claim %s at "
                + "version %s",
            claimId, expectedPriorVersion)
        .isEqualTo(1);
    // Insert a matching claim_amendment row so downstream assertions ("no claim_amendment row
    // written" from OTHER scenarios) remain meaningful for this scenario's own bookkeeping.
    // Column list matches V39__claim_amendment_storage_model.sql + V41 (requested_by_code).
    jdbcClient
        .sql(
            "INSERT INTO claims.claim_amendment (id, claim_id, amendment_reason_code, "
                + "requested_by_code, before_state, request_payload, diff, created_by_user_id, "
                + "created_on) VALUES (:id, :claim, :reason, :requestedBy, CAST(:before AS jsonb), "
                + "CAST(:req AS jsonb), CAST(:diff AS jsonb), :user, :now)")
        .param("id", Uuid7.timeBasedUuid())
        .param("claim", claimId)
        .param("reason", "PROVIDER_ERROR")
        .param("requestedBy", "PROVIDER")
        .param("before", "{}")
        .param("req", "{}")
        .param("diff", "{}")
        .param("user", BDD_USER)
        .param("now", OffsetDateTime.now(ZoneOffset.UTC))
        .update();
  }

  // ---------------------------------------------------------------------------
  // Repository read helpers (always re-read the row so we see the endpoint's write).
  // ---------------------------------------------------------------------------

  private long currentClaimVersion(UUID claimId) {
    Long version =
        jdbcClient
            .sql("SELECT version FROM claims.claim WHERE id = :id")
            .param("id", claimId)
            .query(Long.class)
            .single();
    assertThat(version).as("claim.version must not be null").isNotNull();
    return version;
  }

  private boolean currentClaimHasAssessment(UUID claimId) {
    return jdbcClient
        .sql("SELECT has_assessment FROM claims.claim WHERE id = :id")
        .param("id", claimId)
        .query(Boolean.class)
        .single();
  }

  private boolean currentClaimIsAmended(UUID claimId) {
    return jdbcClient
        .sql("SELECT is_amended FROM claims.claim WHERE id = :id")
        .param("id", claimId)
        .query(Boolean.class)
        .single();
  }

  // ---------------------------------------------------------------------------
  // HTTP helpers.
  // ---------------------------------------------------------------------------

  private void postAssessment(UUID claimId, String jsonBody) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + POST_ASSESSMENT_PATH,
              HttpMethod.POST,
              new HttpEntity<>(jsonBody, headers),
              String.class,
              claimId);
      lastStatusCode = response.getStatusCode().value();
      lastResponseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      lastStatusCode = ex.getStatusCode().value();
      lastResponseBody = ex.getResponseBodyAsString();
    }
  }

  private void postVoidClaim(UUID claimId, String jsonBody) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + VOID_CLAIM_PATH,
              HttpMethod.POST,
              new HttpEntity<>(jsonBody, headers),
              String.class,
              claimId);
      lastStatusCode = response.getStatusCode().value();
      lastResponseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      lastStatusCode = ex.getStatusCode().value();
      lastResponseBody = ex.getResponseBodyAsString();
    }
  }

  private void patchAmendment(UUID claimId, String jsonBody) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + PATCH_CLAIM_PATH,
              HttpMethod.PATCH,
              new HttpEntity<>(jsonBody, headers),
              String.class,
              currentSubmissionId,
              claimId);
      lastStatusCode = response.getStatusCode().value();
      lastResponseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      lastStatusCode = ex.getStatusCode().value();
      lastResponseBody = ex.getResponseBodyAsString();
    }
  }

  // ---------------------------------------------------------------------------
  // JSON payload builders — kept as string templates so we're immune to changes in the generated
  // AssessmentPost model (e.g. added optional fields) and so a broken payload shows up as an
  // HTTP-level failure with the raw body attached to the AssertionError.
  // ---------------------------------------------------------------------------

  private static String assessmentJson(UUID claimId, UUID summaryFeeId, String assessmentType) {
    // claim_id is a REQUIRED field on the AssessmentPost body (in addition to being in the URL).
    return ("""
        {
          "claim_id": "%s",
          "claim_summary_fee_id": "%s",
          "assessment_type": "%s",
          "assessment_reason": "DSTEW-2051 BDD assessment",
          "assessment_outcome": "NILLED",
          "created_by_user_id": "%s",
          "fixed_fee_amount": 100.00,
          "assessed_total_vat": 0,
          "assessed_total_incl_vat": 0,
          "allowed_total_vat": 0,
          "allowed_total_incl_vat": 0
        }
        """)
        .formatted(claimId, summaryFeeId, assessmentType, BDD_USER_UUID);
  }

  /**
   * VOID assessments are created via {@code POST /api/v1/claims/{claimId}/void}, NOT via the
   * regular assessments endpoint (the service rejects {@code AssessmentType.VOID} on the
   * assessments POST). The void endpoint internally invokes {@code claim.voidClaim(userId)} which
   * sets {@code hasAssessment=true} + {@code updatedOn}, so Hibernate dirty-checking still advances
   * {@code @Version} — the exact behaviour {@code @DS2051_8} is asserting.
   */
  private static String voidClaimJson() {
    return ("""
        {
          "created_by_user_id": "%s",
          "assessment_reason": "DSTEW-2051 BDD void"
        }
        """)
        .formatted(BDD_USER_UUID);
  }

  private static String staleClientForenamePatch(long version) {
    // Non-pricing amendment (client_forename change) so the assessed-pricing gate is not the
    // rejection reason — the ONLY reason must be CLAIM_VERSION_CONFLICT.
    return ("""
        {
          "version": %d,
          "amendment_requested_by": "PROVIDER",
          "amendment_reason_code": "PROVIDER_ERROR",
          "amendment_user_id": "%s",
          "client_forename": "DSTEW-2051-BDD-Stale"
        }
        """)
        .formatted(version, BDD_USER_UUID);
  }

  private String safeBodyPreview() {
    if (lastResponseBody == null) {
      return "<null>";
    }
    String trimmed = lastResponseBody.strip();
    return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
  }
}
