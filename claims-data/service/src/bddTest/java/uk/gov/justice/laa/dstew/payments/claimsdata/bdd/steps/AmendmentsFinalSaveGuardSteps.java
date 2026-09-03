package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.PATCH_CLAIM_AMENDMENT_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ClaimValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentCommitService;

/**
 * Step glue for {@code amendmentsFinalSaveGuard.feature} — DSTEW-1753.
 *
 * <p>Proves the commit-phase optimistic-concurrency guard on {@link ClaimAmendmentCommitService} —
 * the guard that catches Hibernate's {@code OptimisticLockException} at merge+flush time and emits
 * the structured WARN log with {@code conflictPoint=final_save}. Complements the initial-check
 * guard already covered by DSTEW-1751/1752 scenarios.
 *
 * <h2>How BDD simulates a mid-flight concurrent version bump</h2>
 *
 * The amendment flow runs in a single HTTP request:
 *
 * <ol>
 *   <li>Prepare — read the {@link Claim} at version N (Phase 1).
 *   <li>Validate — includes {@link ValidationService#validateClaim} (Phase 2). The
 *       DSTEW-2301 harness mocks this bean.
 *   <li>Commit — {@code merge + flush} inside REQUIRES_NEW (Phase 3).
 * </ol>
 *
 * For the final-save guard to fire, the DB row must be bumped <em>after</em> Phase 1's read and
 * <em>before</em> Phase 3's flush. Threading is fragile in a BDD run, so instead this class hooks
 * the mocked {@code validateClaim} with a {@link org.mockito.stubbing.Answer} that executes a
 * native SQL bump of {@code claim.version} as its side effect. Prepare has already loaded the
 * detached claim at version N; the bump lands mid-Phase-2; the commit's versioned UPDATE ...
 * WHERE version = N then matches zero rows and Hibernate throws.
 *
 * <p>The initial-check gate ({@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimVersionValidationStep})
 * runs BEFORE {@link ValidationService#validateClaim} in {@code ClaimAmendmentValidationService},
 * so it always passes: the submitted version and the freshly-read {@code beforeState} version are
 * both N at that point. Only the final-save flush surfaces the bump.
 *
 * <h2>WARN log capture</h2>
 *
 * A logback {@link ListAppender} is attached to {@link ClaimAmendmentCommitService}'s logger in
 * the tag-scoped {@code @Before} hook and detached in {@code @After}. Scenarios assert on the
 * captured events without needing to alter production logging config.
 *
 * <p>Every step body is wrapped in
 * {@link uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step}.
 */
@Slf4j
public class AmendmentsFinalSaveGuardSteps {

  private static final String AMENDMENT_PAYLOAD_MARKER_FORENAME = "Harness-Canary";

  @Autowired private ClaimRepository claimRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private ValidationService validationService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;
  @Autowired private ObjectMapper objectMapper;

  private ListAppender<ILoggingEvent> commitLogAppender;
  private Logger commitServiceLogger;

  // ---------------------------------------------------------------------------
  // Lifecycle — attach + detach the WARN capture ONLY for @dstew-1753 scenarios
  // so we don't pollute unrelated tests with an extra appender.
  // ---------------------------------------------------------------------------

  @Before("@dstew-1753")
  public void attachCommitServiceLogCapture() {
    commitServiceLogger = (Logger) LoggerFactory.getLogger(ClaimAmendmentCommitService.class);
    commitLogAppender = new ListAppender<>();
    commitLogAppender.start();
    commitServiceLogger.addAppender(commitLogAppender);
  }

  @After("@dstew-1753")
  public void detachCommitServiceLogCapture() {
    if (commitServiceLogger != null && commitLogAppender != null) {
      commitServiceLogger.detachAppender(commitLogAppender);
    }
  }

  // ---------------------------------------------------------------------------
  // Given — arm the "concurrent writer" side-effect on the mocked ValidationService.
  // ---------------------------------------------------------------------------

  @Given("a concurrent writer will advance claim.version by {int} during external validation")
  public void aConcurrentWriterWillAdvanceClaimVersionBy(int delta) {
    step(
        "hook the mocked ValidationService.validateClaim(...) to bump claim.version by "
            + delta
            + " as its side effect, simulating a concurrent writer landing between prepare and"
            + " commit",
        () -> armConcurrentWriter(delta, false));
  }

  @Given(
      "a concurrent writer will flip claim.has_assessment to true and advance claim.version by"
          + " {int} during external validation")
  public void aConcurrentWriterWillFlipHasAssessmentAndAdvanceClaimVersionBy(int delta) {
    step(
        "hook the mocked ValidationService.validateClaim(...) to bump claim.version by "
            + delta
            + " AND set has_assessment=true as its side effect, simulating an assessment-shaped"
            + " concurrent writer",
        () -> armConcurrentWriter(delta, true));
  }

  private void armConcurrentWriter(int delta, boolean alsoFlipHasAssessment) {
    UUID claimId = sharedPatchContext.getClaimId();
    assertThat(claimId)
        .as("claimId must be seeded before arming the concurrent writer")
        .isNotNull();

    // Use REQUIRES_NEW so the SQL bump runs in a fresh transaction that commits before the
    // amendment orchestrator's Phase-3 merge/flush sees the row. Belt-and-braces against any
    // parent OSIV / read-only transaction lingering on this thread.
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    doAnswer(
            invocation -> {
              if (concurrentBumpFired) {
                return happyClaimResult();
              }
              concurrentBumpFired = true;
              String sql =
                  alsoFlipHasAssessment
                      ? "UPDATE claims.claim SET version = version + :delta,"
                          + " has_assessment = true WHERE id = :id"
                      : "UPDATE claims.claim SET version = version + :delta WHERE id = :id";
              int updated =
                  txTemplate.execute(
                      status ->
                          jdbcClient
                              .sql(sql)
                              .param("delta", delta)
                              .param("id", claimId)
                              .update());
              assertThat(updated)
                  .as(
                      "concurrent-writer SQL must update exactly one row for claim %s",
                      claimId)
                  .isEqualTo(1);
              log.info(
                  "[DSTEW-1753] Concurrent writer fired: bumped version by {} (has_assessment"
                      + "={}) for claim {}",
                  delta,
                  alsoFlipHasAssessment,
                  claimId);
              return happyClaimResult();
            })
        .when(validationService)
        .validateClaim(any(), any());
  }

  private boolean concurrentBumpFired;

  private static ClaimValidationResult happyClaimResult() {
    ClaimValidationResult result = ClaimValidationResult.builder().build();
    result.setValid(true);
    return result;
  }

  // ---------------------------------------------------------------------------
  // When — repeat submit (reuses the shared patch context populated by the harness).
  // ---------------------------------------------------------------------------

  @When("I submit the same amendment payload again")
  public void iSubmitTheSameAmendmentPayloadAgain() {
    step(
        "PATCH the amendment endpoint again with the same submissionId/claimId/patchJson from the"
            + " shared context — the payload still carries the original submitted version, which"
            + " is now stale in the DB",
        () -> {
          assertThat(sharedPatchContext.isPopulated())
              .as("shared patch context must be populated from the first submit")
              .isTrue();
          api.patchClaimAmendment(
              sharedPatchContext.getSubmissionId(),
              sharedPatchContext.getClaimId(),
              sharedPatchContext.getPatchJson());
          log.info(
              "[DSTEW-1753] Second PATCH amendment for claim {} → status={}",
              sharedPatchContext.getClaimId(),
              scenarioContext.getLastStatusCode());
        });
  }

  // ---------------------------------------------------------------------------
  // Then — HTTP + envelope assertions.
  // ---------------------------------------------------------------------------

  @Then(
      "the amendment is rejected with HTTP {int} and amendment error code {string}")
  public void theAmendmentIsRejectedWithHttpAndAmendmentErrorCode(int status, String code) {
    step(
        "assert HTTP status == "
            + status
            + " and the response body's errors[*].code contains \""
            + code
            + "\"",
        () -> {
          assertThat(scenarioContext.getLastStatusCode())
              .as(
                  "last HTTP status; body=%s",
                  scenarioContext.getLastResponseBody() == null
                      ? "<null>"
                      : scenarioContext.getLastResponseBody().toString())
              .isEqualTo(status);
          List<String> codes = extractErrorCodes(scenarioContext.getLastResponseBody());
          assertThat(codes)
              .as("errors[*].code in response body")
              .contains(code);
        });
  }

  @Then("the response body is an RFC 9457 ProblemDetail with status {int}")
  public void theResponseBodyIsAnRfc9457ProblemDetailWithStatus(int status) {
    step(
        "assert the response body parses as a RFC 9457 ProblemDetail with status == " + status,
        () -> {
          JsonNode body = scenarioContext.getLastResponseBody();
          assertThat(body).as("response body").isNotNull();
          assertThat(body.path("status").asInt(-1))
              .as("ProblemDetail 'status' field")
              .isEqualTo(status);
          assertThat(
                  body.hasNonNull("title")
                      || body.hasNonNull("detail")
                      || body.hasNonNull("type")
                      || body.hasNonNull("instance"))
              .as("ProblemDetail body must expose at least one standard field (body=%s)", body)
              .isTrue();
        });
  }

  @Then(
      "the response body's errors array carries exactly one entry with code {string}")
  public void theResponseBodyErrorsArrayCarriesExactlyOneEntryWithCode(String code) {
    step(
        "assert the response body's errors[] array has exactly one entry whose code equals \""
            + code
            + "\"",
        () -> {
          JsonNode body = scenarioContext.getLastResponseBody();
          assertThat(body).as("response body").isNotNull();
          JsonNode errors = body.path("errors");
          assertThat(errors.isArray())
              .as("response body must carry an 'errors' array (body=%s)", body)
              .isTrue();
          assertThat(errors)
              .as("response body's 'errors' array size")
              .hasSize(1);
          assertThat(errors.get(0).path("code").asText())
              .as("errors[0].code")
              .isEqualTo(code);
        });
  }

  // ---------------------------------------------------------------------------
  // Then — persisted-state assertions specific to DSTEW-1753.
  // ---------------------------------------------------------------------------

  @Then("claim.is_amended is false")
  public void claimIsAmendedIsFalse() {
    step(
        "assert claim.is_amended = false",
        () -> assertThat(requireClaim().isAmended()).as("claim.is_amended").isFalse());
  }

  @Then("claim.version equals {long}")
  public void claimVersionEquals(long expected) {
    step(
        "assert claim.version = " + expected,
        () ->
            assertThat(requireClaim().getVersion())
                .as("claim.version")
                .isEqualTo(expected));
  }

  @Then("claim.has_assessment is true")
  public void claimHasAssessmentIsTrue() {
    step(
        "assert claim.has_assessment = true",
        () ->
            assertThat(requireClaim().isHasAssessment())
                .as("claim.has_assessment")
                .isTrue());
  }

  // ---------------------------------------------------------------------------
  // Then — WARN log assertions.
  // ---------------------------------------------------------------------------

  @Then("a WARN log entry from the final-save guard was captured")
  public void aWarnLogEntryFromTheFinalSaveGuardWasCaptured() {
    step(
        "assert the log capture attached to ClaimAmendmentCommitService recorded at least one"
            + " WARN entry — proves the guard reached the catch block and emitted its structured"
            + " diagnostic",
        () ->
            assertThat(warnEntries())
                .as("WARN log entries captured on ClaimAmendmentCommitService")
                .isNotEmpty());
  }

  @Then("the captured WARN log contains {string}")
  public void theCapturedWarnLogContains(String needle) {
    step(
        "assert at least one captured WARN entry individually contains \"" + needle + "\" —"
            + " avoids false positives from tokens spread across separate log lines",
        () ->
            assertThat(warnMessages())
                .as("captured WARN entries on ClaimAmendmentCommitService")
                .anyMatch(msg -> msg.contains(needle)));
  }

  @Then("the captured WARN log contains the current claim id")
  public void theCapturedWarnLogContainsTheCurrentClaimId() {
    step(
        "assert at least one captured WARN entry individually references the current claimId",
        () -> {
          UUID claimId = sharedPatchContext.getClaimId();
          assertThat(claimId).as("current claim id").isNotNull();
          String token = "claimId=" + claimId;
          assertThat(warnMessages())
              .as("captured WARN entries on ClaimAmendmentCommitService")
              .anyMatch(msg -> msg.contains(token));
        });
  }

  @Then("the captured WARN log does not carry any amendment payload field values")
  public void theCapturedWarnLogDoesNotCarryAnyAmendmentPayloadFieldValues() {
    step(
        "assert no captured WARN entry contains the amendment payload's field-value markers —"
            + " proves the guard's structured log carries only the whitelisted safe fields",
        () -> {
          String warnBody = allWarnFormatted();
          // The harness's non-pricing payload embeds AMENDMENT_PAYLOAD_MARKER_FORENAME
          // (see AmendmentHarnessCommonSteps#buildNonPricingPatch). If the guard leaked payload
          // data into its structured log, this literal would appear.
          assertThat(warnBody.toLowerCase(Locale.ROOT))
              .as("WARN log must not carry payload values")
              .doesNotContain(AMENDMENT_PAYLOAD_MARKER_FORENAME.toLowerCase(Locale.ROOT));
          // Belt & braces: the ClaimAmendmentValidationCode template for the shared conflict
          // message carries no dynamic values; the guard log format string
          // ("event=... claimId=... submittedClaimVersion=... conflictPoint=...") is checked
          // separately by the positive-assertion steps above.
          assertThat(warnBody)
              .as("WARN log must not carry the amendment_reason_code payload literal")
              .doesNotContain("PROVIDER_ERROR");
          assertThat(warnBody)
              .as("WARN log must not carry the amendment_requested_by payload literal")
              .doesNotContain("PROVIDER");
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers.
  // ---------------------------------------------------------------------------

  private Claim requireClaim() {
    UUID claimId = sharedPatchContext.getClaimId();
    assertThat(claimId).as("current claim id").isNotNull();
    return claimRepository
        .findById(claimId)
        .orElseThrow(() -> new AssertionError("Claim missing after PATCH: " + claimId));
  }

  private List<ILoggingEvent> warnEntries() {
    return commitLogAppender.list.stream()
        .filter(e -> e.getLevel() == Level.WARN)
        .toList();
  }

  private List<String> warnMessages() {
    return warnEntries().stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private String allWarnFormatted() {
    StringBuilder sb = new StringBuilder();
    for (ILoggingEvent e : warnEntries()) {
      sb.append(e.getFormattedMessage()).append('\n');
    }
    return sb.toString();
  }

  private static List<String> extractErrorCodes(JsonNode body) {
    if (body == null) {
      return List.of();
    }
    JsonNode errors = body.path("errors");
    if (!errors.isArray()) {
      return List.of();
    }
    return errors.findValuesAsText("code");
  }

  // ===========================================================================
  // DSTEW-1753 @DS1753_8 — real two-thread race.
  //
  // Complements the deterministic single-thread simulation in scenarios 1–7 by
  // dispatching two independent HTTP PATCH amendments against the SAME claimId
  // on two executor threads. Both threads rendezvous inside the mocked
  // ValidationService.validateClaim(...) on a CyclicBarrier(2) so they release
  // together and race into Phase 3's merge+flush. Hibernate's versioned UPDATE
  // guarantees exactly one commit wins and exactly one hits
  // OptimisticLockException.
  // ===========================================================================

  /** Timeout for both the rendezvous barrier and the whole race dispatch. */
  private static final int RACE_RENDEZVOUS_TIMEOUT_SECONDS = 10;

  private static final int RACE_DISPATCH_TIMEOUT_SECONDS = 60;

  /** Populated by the @When race step; drained by the @Then race assertions. */
  private final List<RaceOutcome> raceOutcomes = Collections.synchronizedList(new ArrayList<>());

  @Given("two threads will submit a well-formed non-pricing amendment simultaneously")
  public void twoThreadsWillSubmitAWellFormedNonPricingAmendmentSimultaneously() {
    step(
        "arm a CyclicBarrier(2) inside the mocked ValidationService.validateClaim(...) so both"
            + " racing threads park there and release together, guaranteeing they both enter"
            + " Phase 3 (merge+flush) with the same detached claim.version = N",
        () -> {
          UUID claimId = sharedPatchContext.getClaimId();
          assertThat(claimId)
              .as("claimId must be seeded before arming the race barrier")
              .isNotNull();

          CyclicBarrier barrier = new CyclicBarrier(2);
          doAnswer(
                  invocation -> {
                    try {
                      barrier.await(RACE_RENDEZVOUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                      throw new IllegalStateException(
                          "race rendezvous barrier failed for claim " + claimId, ex);
                    }
                    return happyClaimResult();
                  })
              .when(validationService)
              .validateClaim(any(), any());
          log.info(
              "[DSTEW-1753] Race barrier armed on ValidationService.validateClaim for claim {}",
              claimId);
        });
  }

  @When("both amendments are dispatched and rendezvous at the final-save boundary")
  public void bothAmendmentsAreDispatchedAndRendezvousAtTheFinalSaveBoundary() {
    step(
        "dispatch 2× PATCH amendment against the same submissionId/claimId on 2 executor threads;"
            + " both threads block inside the mocked validate() on the shared CyclicBarrier, are"
            + " released together, then race into ClaimAmendmentCommitService.commit() —"
            + " Hibernate's versioned UPDATE serialises them, one wins, one throws"
            + " OptimisticLockException",
        () -> {
          assertThat(sharedPatchContext.isPopulated())
              .as("shared patch context must be populated before racing")
              .isTrue();
          UUID submissionId = sharedPatchContext.getSubmissionId();
          UUID claimId = sharedPatchContext.getClaimId();
          String payload = sharedPatchContext.getPatchJson();

          raceOutcomes.clear();
          ExecutorService pool = Executors.newFixedThreadPool(2);
          try {
            List<Callable<RaceOutcome>> tasks =
                List.of(
                    () -> dispatchRaceRequest(submissionId, claimId, payload, "race-A"),
                    () -> dispatchRaceRequest(submissionId, claimId, payload, "race-B"));
            List<Future<RaceOutcome>> futures =
                pool.invokeAll(tasks, RACE_DISPATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (Future<RaceOutcome> f : futures) {
              raceOutcomes.add(f.get());
            }
          } finally {
            pool.shutdownNow();
          }

          assertThat(raceOutcomes)
              .as("both racing dispatches must have completed with a captured outcome")
              .hasSize(2);
          log.info(
              "[DSTEW-1753] Race complete for claim {} — outcomes: {}", claimId, raceOutcomes);
        });
  }

  @Then("exactly one racing amendment returned HTTP 2xx")
  public void exactlyOneRacingAmendmentReturned2xx() {
    step(
        "assert exactly one of the two race outcomes has a 2xx status — the winning writer",
        () ->
            assertThat(raceOutcomes.stream().filter(o -> o.status() / 100 == 2).toList())
                .as("2xx race outcomes (all=%s)", raceOutcomes)
                .hasSize(1));
  }

  @Then(
      "exactly one racing amendment was rejected with HTTP {int} and amendment error code {string}")
  public void exactlyOneRacingAmendmentWasRejectedWithHttpAndAmendmentErrorCode(
      int status, String code) {
    step(
        "assert exactly one race outcome has HTTP "
            + status
            + " AND its response body's errors[*].code contains \""
            + code
            + "\" — the losing writer bounced by the final-save guard",
        () -> {
          List<RaceOutcome> rejected =
              raceOutcomes.stream().filter(o -> o.status() == status).toList();
          assertThat(rejected)
              .as("race outcomes with HTTP " + status + " (all=%s)", raceOutcomes)
              .hasSize(1);
          List<String> codes = extractErrorCodes(rejected.get(0).body());
          assertThat(codes)
              .as("errors[*].code on the rejected race outcome")
              .contains(code);
        });
  }

  /**
   * Race-safe PATCH dispatch — does NOT stash into the shared {@link BddScenarioContext} because
   * two threads would clobber each other; instead returns the (status, body) tuple so the
   * scenario's @Then steps can assert on both outcomes.
   */
  private RaceOutcome dispatchRaceRequest(
      UUID submissionId, UUID claimId, String payload, String label) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + PATCH_CLAIM_AMENDMENT_PATH,
              HttpMethod.PATCH,
              new HttpEntity<>(payload, headers),
              String.class,
              submissionId,
              claimId);
      int status = response.getStatusCode().value();
      JsonNode body = parseBodyOrNull(response.getBody());
      log.info("[DSTEW-1753] Race outcome {}: HTTP {} body={}", label, status, body);
      return new RaceOutcome(label, status, body);
    } catch (HttpStatusCodeException ex) {
      int status = ex.getStatusCode().value();
      JsonNode body = parseBodyOrNull(ex.getResponseBodyAsString());
      log.info("[DSTEW-1753] Race outcome {}: HTTP {} body={}", label, status, body);
      return new RaceOutcome(label, status, body);
    }
  }

  private JsonNode parseBodyOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(raw);
    } catch (Exception ex) {
      return null;
    }
  }

  /** Immutable tuple carrying one race participant's HTTP outcome. */
  private record RaceOutcome(String label, int status, JsonNode body) {
    @Override
    public String toString() {
      return label + "->HTTP " + status;
    }
  }
}







