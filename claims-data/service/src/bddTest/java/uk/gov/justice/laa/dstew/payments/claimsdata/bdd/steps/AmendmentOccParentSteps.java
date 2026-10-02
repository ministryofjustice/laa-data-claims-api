package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ClaimValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimVersionValidationStep;

/**
 * Step definitions for {@code amendmentsOccParent.feature} (DSTEW-1658) — the parent-level OCC
 * contract sweep.
 *
 * <p>Owns the cross-cutting OCC glue: seeding a claim at a specific stored {@code version}, a
 * payload-shape composer that maps the readable {@code claim_version=N} / {@code omit
 * claim_version} / {@code ... with concurrent commit advancing to N before save} labels onto the
 * real {@code version} field (and arms a concurrent writer for the final-guard case), the OCC
 * HTTP-status and response-body assertions, and the early-gate structured-log assertions.
 *
 * <h2>Concurrent-writer simulation (final guard)</h2>
 *
 * <p>Mirrors the proven DSTEW-1753 mechanism: {@link ValidationService} is a
 * {@code @MockitoSpyBean} (real by default, reset between scenarios by {@code
 * BddAmendmentResetHook}). For the final-guard shape we override {@code validateClaim} with a
 * side-effect that bumps {@code claim.version} via a native SQL UPDATE in a REQUIRES_NEW
 * transaction and returns a happy result. The early version-gate has already passed (submitted ==
 * stored at that point); the bump lands before the commit's merge+flush, so Hibernate's versioned
 * UPDATE matches zero rows and raises {@code OptimisticLockException} — the {@code
 * conflictPoint=final_save} guard. All other shapes leave the spy real.
 *
 * <h2>Early-gate structured-log capture</h2>
 *
 * <p>A logback {@link ListAppender} is attached to {@link ClaimVersionValidationStep}'s logger in a
 * {@code @dstew-1658}-scoped {@code @Before} hook (the DSTEW-1753 harness captures the {@code
 * final_save} logger on {@code ClaimAmendmentCommitService}; the parent's early-stale scenario
 * needs the {@code initial_check} logger instead).
 */
@Slf4j
public class AmendmentOccParentSteps {

  private static final String VALID_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-165800000001";
  private static final String VALID_REQUESTED_BY = "PROVIDER";
  private static final String VALID_REASON = "PROVIDER_ERROR";
  private static final String NON_PRICING_SURNAME = "Jones";

  // Conflict-specific "canary" business change. case_start_date is a member of BOTH
  // PdaRequestField (CASE_START_DATE -> impactsPda when not PROD-with-concluded) and
  // FeeSchemeRequestField (START_DATE, all areas), so an absent-conflict submit actually
  // dispatches outbound PDA + FSP calls. (The previous client_surname-only change touched
  // NEITHER field set, so the application short-circuited PDA/FSP regardless of the version
  // gate — meaning the "no PDA/FSP call" and "no calculated_fee_detail row" guarantees could
  // stay green even if the OCC short-circuit regressed. See PR #507 Copilot review.) The
  // fixture baseline caseStartDate is 01/07/2025, so 04/08/2025 is a genuine diff; it is the
  // same proven-valid value used by the sibling pricing-amendment harness steps.
  private static final String CANARY_CASE_START_DATE = "04/08/2025";

  private static final Pattern CLAIM_VERSION = Pattern.compile("claim_version=(\\d+)");
  private static final Pattern ADVANCING_TO = Pattern.compile("advancing to (\\d+)");

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private AmendableClaimFixture fixture;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private ValidationService validationService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcClient jdbcClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ListAppender<ILoggingEvent> versionStepLogAppender;
  private Logger versionStepLogger;
  private boolean concurrentBumpFired;

  // ---------------------------------------------------------------------------
  // Lifecycle — attach the early-gate WARN capture ONLY for @dstew-1658 scenarios.
  // ---------------------------------------------------------------------------

  @Before("@dstew-1658")
  public void attachVersionStepLogCapture() {
    versionStepLogger = (Logger) LoggerFactory.getLogger(ClaimVersionValidationStep.class);
    versionStepLogAppender = new ListAppender<>();
    versionStepLogAppender.start();
    versionStepLogger.addAppender(versionStepLogAppender);
  }

  @After("@dstew-1658")
  public void detachVersionStepLogCapture() {
    if (versionStepLogger != null && versionStepLogAppender != null) {
      versionStepLogger.detachAppender(versionStepLogAppender);
    }
  }

  // ---------------------------------------------------------------------------
  // Given — seed + payload composer
  // ---------------------------------------------------------------------------

  @Given("an original claim exists at stored version {int}")
  public void anOriginalClaimExistsAtStoredVersion(int storedVersion) {
    AmendableClaimFixture.Seeded seeded =
        fixture.legalHelpValid().withVersion(storedVersion).seed();
    sharedPatchContext.setSubmissionId(seeded.submissionId());
    sharedPatchContext.setClaimId(seeded.claimId());
    sharedPatchContext.setBaselineClaimVersion(seeded.baselineVersion());
    sharedPatchContext.setBaselineCfdCount(countCfd(seeded.claimId()));
  }

  @Given("an amendment payload built as {string}")
  public void anAmendmentPayloadBuiltAs(String shape) {
    ObjectNode patch = baseValidPatch();
    String trimmed = shape.trim();

    if ("omit claim_version".equals(trimmed)) {
      // Leave the version off entirely — the early gate treats an absent version as a null-version
      // request-validation failure (400).
      publish(patch);
      return;
    }

    Matcher versionMatcher = CLAIM_VERSION.matcher(trimmed);
    if (!versionMatcher.find()) {
      throw new IllegalArgumentException("Unsupported payload shape: " + shape);
    }
    long submittedVersion = Long.parseLong(versionMatcher.group(1));
    patch.put("version", submittedVersion);

    Matcher advanceMatcher = ADVANCING_TO.matcher(trimmed);
    if (advanceMatcher.find()) {
      // Final-guard shape: arm a concurrent writer that advances the stored version from the
      // submitted value to the target, so the final merge+flush raises OptimisticLockException.
      long advancedTarget = Long.parseLong(advanceMatcher.group(1));
      armConcurrentWriter((int) (advancedTarget - submittedVersion));
    }
    publish(patch);
  }

  // ---------------------------------------------------------------------------
  // Then — OCC HTTP + body contract
  // ---------------------------------------------------------------------------

  @Then("the OCC endpoint response status is {int}")
  public void theOccEndpointResponseStatusIs(int expected) {
    assertThat(scenarioContext.getLastStatusCode())
        .as("OCC endpoint HTTP status (body=%s)", scenarioContext.getLastResponseBody())
        .isEqualTo(expected);
  }

  @Then("the response body indicates {string}")
  public void theResponseBodyIndicates(String indicator) {
    JsonNode body = scenarioContext.getLastResponseBody();
    switch (indicator) {
      case "none" ->
          assertThat(extractErrorCodes(body))
              .as("a successful amendment (204) must carry no error codes (body=%s)", body)
              .isEmpty();
      case "request-validation" -> {
        assertThat(scenarioContext.getLastStatusCode())
            .as("a request-validation failure must be HTTP 400 (body=%s)", body)
            .isEqualTo(400);
        assertThat(body)
            .as("a 400 request-validation failure must carry a problem/validation body")
            .isNotNull();
      }
      default -> {
        boolean present =
            extractErrorCodes(body).contains(indicator)
                || (body != null && body.toString().contains(indicator));
        assertThat(present)
            .as("response body must surface error code %s (body=%s)", indicator, body)
            .isTrue();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Then — early-gate structured-log assertions (conflictPoint=initial_check)
  // ---------------------------------------------------------------------------

  @Then("the structured conflict log entry contains {string}")
  public void theStructuredConflictLogEntryContains(String needle) {
    assertThat(warnMessages())
        .as("captured WARN entries on ClaimVersionValidationStep")
        .anyMatch(msg -> msg.contains(needle));
  }

  @Then("the structured conflict log entry does not contain any amendment payload values")
  public void theStructuredConflictLogDoesNotContainAnyPayloadValues() {
    String warnBody = allWarnFormatted();
    assertThat(warnBody)
        .as("early-gate WARN log must not carry the client_surname payload value")
        .doesNotContain(NON_PRICING_SURNAME);
    assertThat(warnBody)
        .as("early-gate WARN log must not carry the amendment_reason_code payload value")
        .doesNotContain(VALID_REASON);
    assertThat(warnBody)
        .as("early-gate WARN log must not carry the amendment_requested_by payload value")
        .doesNotContain(VALID_REQUESTED_BY);
    assertThat(warnBody)
        .as("early-gate WARN log must not carry the amendment_user_id payload value")
        .doesNotContain(VALID_USER_ID);
    assertThat(warnBody)
        .as("early-gate WARN log must not carry the case_start_date canary payload value")
        .doesNotContain(CANARY_CASE_START_DATE);
  }

  @Then("the structured conflict log entry does not contain any financial values")
  public void theStructuredConflictLogDoesNotContainAnyFinancialValues() {
    String warnBody = allWarnFormatted().toLowerCase(Locale.ROOT);
    // The early-gate log format carries only event / claimId / version integers / conflictPoint —
    // no fee or disbursement amounts. These markers would appear if a financial value leaked.
    assertThat(warnBody).as("WARN log must not carry an 'amount' marker").doesNotContain("amount");
    assertThat(warnBody).as("WARN log must not carry a currency symbol").doesNotContain("£");
    assertThat(warnBody).as("WARN log must not carry a 'gbp' marker").doesNotContain("gbp");
    assertThat(warnBody)
        .as("WARN log must not carry a decimal/currency-shaped value")
        .doesNotContain(".0");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ObjectNode baseValidPatch() {
    // Valid metadata + a conflict-specific canary change (case_start_date) so a version-matching
    // submit commits a real PRICING amendment (204) that genuinely exercises the PDA and FSP
    // downstream paths — and so a conflict case's "no PDA/FSP call / no calculated_fee_detail row"
    // guarantees have real teeth (they would fail if the OCC short-circuit regressed and let the
    // flow reach PDA/FSP). The non-pricing client_surname is retained purely to give the
    // structured-log no-leak assertion an additional payload value to prove absent.
    ObjectNode node = objectMapper.createObjectNode();
    node.put("amendment_requested_by", VALID_REQUESTED_BY);
    node.put("amendment_reason_code", VALID_REASON);
    node.put("amendment_user_id", VALID_USER_ID);
    node.put("client_surname", NON_PRICING_SURNAME);
    node.put("case_start_date", CANARY_CASE_START_DATE);
    return node;
  }

  private void publish(ObjectNode patch) {
    sharedPatchContext.setPatchJson(patch.toString());
  }

  private void armConcurrentWriter(int delta) {
    UUID claimId = sharedPatchContext.getClaimId();
    assertThat(claimId)
        .as("claimId must be seeded before arming the concurrent writer")
        .isNotNull();
    concurrentBumpFired = false;

    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    doAnswer(
            invocation -> {
              if (concurrentBumpFired) {
                return happyClaimResult();
              }
              concurrentBumpFired = true;
              int updated =
                  txTemplate.execute(
                      status ->
                          jdbcClient
                              .sql(
                                  "UPDATE claims.claim SET version = version + :delta WHERE id = :id")
                              .param("delta", delta)
                              .param("id", claimId)
                              .update());
              assertThat(updated)
                  .as("concurrent-writer SQL must update exactly one row for claim %s", claimId)
                  .isEqualTo(1);
              log.info(
                  "[DSTEW-1658] Concurrent writer bumped version by {} for claim {}",
                  delta,
                  claimId);
              return happyClaimResult();
            })
        .when(validationService)
        .validateClaim(any(), any());
  }

  private static ClaimValidationResult happyClaimResult() {
    ClaimValidationResult result = ClaimValidationResult.builder().build();
    result.setValid(true);
    return result;
  }

  private long countCfd(UUID claimId) {
    return calculatedFeeDetailRepository.findAll().stream()
        .filter(cfd -> cfd.getClaim() != null && claimId.equals(cfd.getClaim().getId()))
        .count();
  }

  private List<ILoggingEvent> warnEntries() {
    return versionStepLogAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
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
}
