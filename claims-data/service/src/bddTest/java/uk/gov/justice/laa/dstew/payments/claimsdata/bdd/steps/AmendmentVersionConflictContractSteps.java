package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentCommitService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimVersionValidationStep;

/**
 * Step glue for {@code amendmentsVersionConflictContract.feature} — DSTEW-1754.
 *
 * <p>Proves the SHARED stale-version conflict contract that both amendment version guards reuse:
 * the HTTP 409 {@code CLAIM_VERSION_CONFLICT} envelope, the exact user-safe message, and the
 * structured WARN logging — identical no matter which guard detected the conflict.
 *
 * <h2>Driving both conflict points</h2>
 *
 * <ul>
 *   <li><b>initial_check</b> ({@link ClaimVersionValidationStep}) — reuses the DSTEW-2301 harness to
 *       seed a claim at a known stored version, then this class's {@code the amendment is submitted
 *       carrying claim version N} overrides the patch's submitted version so it differs, tripping
 *       the early gate.
 *   <li><b>final_save</b> ({@link ClaimAmendmentCommitService}) — reuses the proven DSTEW-1753
 *       concurrent-writer step ({@code a concurrent writer will advance claim.version by N during
 *       external validation}), which bumps the row mid-validation so the commit's merge+flush raises
 *       {@code OptimisticLockException}.
 * </ul>
 *
 * <p>The HTTP/envelope outcome steps ({@code the amendment is rejected with HTTP ... and amendment
 * error code ...}, {@code the response body is an RFC 9457 ProblemDetail ...}, {@code the response
 * body's errors array carries exactly one entry ...}) are reused from {@link
 * AmendmentsFinalSaveGuardSteps}. Only the stale-version-specific assertions — the exact user-safe
 * message and the structured-log safe-fields contract across BOTH conflict-point loggers — are
 * local.
 *
 * <h2>Log capture</h2>
 *
 * A {@link ListAppender} is attached to EACH conflict-point logger ({@link ClaimVersionValidationStep}
 * for initial_check, {@link ClaimAmendmentCommitService} for final_save) in the {@code
 * @dstew-1754}-scoped {@code @Before} hook and detached in {@code @After}. The log assertions search
 * across both so a single step phrase serves either conflict point.
 *
 * <p>Every step body is wrapped in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step}.
 */
@Slf4j
public class AmendmentVersionConflictContractSteps {

  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-175400000001";
  // Marker value embedded in the non-pricing payload — asserted ABSENT from the WARN log to prove
  // the structured diagnostic never leaks amendment payload field values.
  private static final String AMENDMENT_PAYLOAD_MARKER_FORENAME = "Conflict-Canary";

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private ClaimRepository claimRepository;

  private ListAppender<ILoggingEvent> earlyGateAppender;
  private ListAppender<ILoggingEvent> finalGuardAppender;
  private Logger earlyGateLogger;
  private Logger finalGuardLogger;

  // The single CLAIM_VERSION_CONFLICT WARN selected from the EXPECTED conflict-point logger by the
  // point-specific "a stale-version conflict WARN was logged at the ... point" step. Every
  // subsequent field assertion runs against THIS one event, so the scenario proves that a single
  // structured conflict event carries the complete contract (rather than letting tokens be spread
  // across unrelated entries or supplied by the wrong logger).
  private String selectedConflictWarn;
  private String selectedConflictPoint;

  // ---------------------------------------------------------------------------
  // Lifecycle — attach + detach a WARN capture on BOTH conflict-point loggers,
  // scoped to @dstew-1754 so unrelated scenarios are untouched.
  // ---------------------------------------------------------------------------

  @Before("@dstew-1754")
  public void attachConflictLogCaptures() {
    selectedConflictWarn = null;
    selectedConflictPoint = null;
    earlyGateLogger = (Logger) LoggerFactory.getLogger(ClaimVersionValidationStep.class);
    earlyGateAppender = new ListAppender<>();
    earlyGateAppender.start();
    earlyGateLogger.addAppender(earlyGateAppender);

    finalGuardLogger = (Logger) LoggerFactory.getLogger(ClaimAmendmentCommitService.class);
    finalGuardAppender = new ListAppender<>();
    finalGuardAppender.start();
    finalGuardLogger.addAppender(finalGuardAppender);
  }

  @After("@dstew-1754")
  public void detachConflictLogCaptures() {
    if (earlyGateLogger != null && earlyGateAppender != null) {
      earlyGateLogger.detachAppender(earlyGateAppender);
    }
    if (finalGuardLogger != null && finalGuardAppender != null) {
      finalGuardLogger.detachAppender(finalGuardAppender);
    }
  }

  // ---------------------------------------------------------------------------
  // Given — override the submitted version so it differs from the seeded stored
  // version, tripping the early (initial_check) gate.
  // ---------------------------------------------------------------------------

  @Given("the amendment is submitted carrying claim version {long}")
  public void theAmendmentIsSubmittedCarryingClaimVersion(long submittedVersion) {
    step(
        "rebuild the shared patch JSON so the submitted version ("
            + submittedVersion
            + ") differs from the seeded stored version, tripping the initial_check gate",
        () -> {
          assertThat(sharedPatchContext.getClaimId())
              .as("an amendable claim must be seeded before overriding the submitted version")
              .isNotNull();
          sharedPatchContext.setPatchJson(buildNonPricingPatch(submittedVersion));
        });
  }

  // ---------------------------------------------------------------------------
  // Then — the shared user-safe message (identical on both conflict points
  // because it is carried on the CLAIM_VERSION_CONFLICT error entry).
  // ---------------------------------------------------------------------------

  @Then("the stale-version error message is {string}")
  public void theStaleVersionErrorMessageIs(String expected) {
    step(
        "assert the response body's errors[0].message equals the approved user-safe wording \""
            + expected
            + "\" — the guard-independent equivalence anchor",
        () -> {
          JsonNode body = scenarioContext.getLastResponseBody();
          assertThat(body).as("response body").isNotNull();
          JsonNode errors = body.path("errors");
          assertThat(errors.isArray())
              .as("response body must carry an 'errors' array (body=%s)", body)
              .isTrue();
          assertThat(errors).as("errors array").isNotEmpty();
          assertThat(errors.get(0).path("message").asText())
              .as("errors[0].message (user-safe stale-version wording)")
              .isEqualTo(expected);
        });
  }

  // ---------------------------------------------------------------------------
  // Then — structured WARN log assertions, per conflict point.
  // ---------------------------------------------------------------------------

  @Then("a stale-version conflict WARN was logged at the initial_check point")
  public void aStaleVersionConflictWarnWasLoggedAtInitialCheck() {
    step(
        "select the single CLAIM_VERSION_CONFLICT WARN from the ClaimVersionValidationStep logger —"
            + " proves the early gate emitted its structured diagnostic and anchors every"
            + " subsequent field assertion to that one event",
        () -> selectSingleConflictWarn(earlyGateAppender, "initial_check"));
  }

  @Then("a stale-version conflict WARN was logged at the final_save point")
  public void aStaleVersionConflictWarnWasLoggedAtFinalSave() {
    step(
        "select the single CLAIM_VERSION_CONFLICT WARN from the ClaimAmendmentCommitService logger —"
            + " proves the final guard reached its catch block and anchors every subsequent field"
            + " assertion to that one event",
        () -> selectSingleConflictWarn(finalGuardAppender, "final_save"));
  }

  @Then("the stale-version conflict log contains {string}")
  public void theStaleVersionConflictLogContains(String needle) {
    step(
        "assert the SELECTED conflict event contains \"" + needle + "\"",
        () ->
            assertThat(selectedConflictWarn())
                .as("the selected %s CLAIM_VERSION_CONFLICT WARN", selectedConflictPoint)
                .contains(needle));
  }

  @Then("the stale-version conflict log contains the current stored claim version")
  public void theStaleVersionConflictLogContainsTheCurrentStoredClaimVersion() {
    step(
        "assert the selected conflict event carries currentClaimVersion=<the real stored version> —"
            + " the early gate has the stored version in hand so logs it (the 'where available'"
            + " field)",
        () -> {
          long storedVersion = requireClaim().getVersion();
          String token = "currentClaimVersion=" + storedVersion;
          assertThat(selectedConflictWarn())
              .as("the selected %s conflict event must carry %s", selectedConflictPoint, token)
              .contains(token);
        });
  }

  @Then("the stale-version conflict log does not contain a current claim version")
  public void theStaleVersionConflictLogDoesNotContainACurrentClaimVersion() {
    step(
        "assert the selected conflict event OMITS currentClaimVersion= — at the final guard the row"
            + " was advanced by a concurrent writer so the current version is not available (the"
            + " 'where available' omission); a stale/fabricated value here must fail the scenario",
        () ->
            assertThat(selectedConflictWarn())
                .as(
                    "the selected %s conflict event must omit the current claim version",
                    selectedConflictPoint)
                .doesNotContain("currentClaimVersion="));
  }

  @Then("the stale-version conflict log contains the current claim id")
  public void theStaleVersionConflictLogContainsTheCurrentClaimId() {
    step(
        "assert the selected conflict event references the current claimId",
        () -> {
          UUID claimId = sharedPatchContext.getClaimId();
          assertThat(claimId).as("current claim id").isNotNull();
          String token = "claimId=" + claimId;
          assertThat(selectedConflictWarn())
              .as("the selected %s conflict event must carry %s", selectedConflictPoint, token)
              .contains(token);
        });
  }

  @Then("the stale-version conflict log carries no amendment payload or financial values")
  public void theStaleVersionConflictLogCarriesNoPayloadOrFinancialValues() {
    step(
        "assert the selected conflict event contains no amendment payload field values or"
            + " financial-field tokens — proves the structured log carries only the whitelisted"
            + " safe fields",
        () -> {
          String warnBody = selectedConflictWarn().toLowerCase(Locale.ROOT);
          // Amendment payload values must never appear.
          assertThat(warnBody)
              .as("WARN log must not carry the client_forename payload value")
              .doesNotContain(AMENDMENT_PAYLOAD_MARKER_FORENAME.toLowerCase(Locale.ROOT));
          assertThat(warnBody)
              .as("WARN log must not carry the amendment_reason_code payload literal")
              .doesNotContain("provider_error");
          assertThat(warnBody)
              .as("WARN log must not carry the amendment_requested_by payload literal")
              .doesNotContain("provider");
          // Financial-field tokens would only appear if monetary/fee detail leaked into the log.
          assertThat(warnBody)
              .as("WARN log must not carry fee/financial field tokens")
              .doesNotContain("netprofit")
              .doesNotContain("netdisbursement")
              .doesNotContain("feecalculation")
              .doesNotContain("calculatedfee")
              .doesNotContain("amount");
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers.
  // ---------------------------------------------------------------------------

  /**
   * Selects the single {@code CLAIM_VERSION_CONFLICT} WARN emitted by the given conflict-point
   * logger and remembers it, so every later field assertion runs against that one event. Requiring
   * exactly one match on the EXPECTED logger prevents a token being satisfied by an unrelated entry
   * or by the other conflict point's logger.
   */
  private void selectSingleConflictWarn(ListAppender<ILoggingEvent> appender, String point) {
    List<String> conflicts =
        warnMessages(appender).stream()
            .filter(msg -> msg.contains("event=CLAIM_VERSION_CONFLICT"))
            .toList();
    assertThat(conflicts)
        .as("exactly one CLAIM_VERSION_CONFLICT WARN at the %s point", point)
        .hasSize(1);
    this.selectedConflictWarn = conflicts.get(0);
    this.selectedConflictPoint = point;
  }

  private String selectedConflictWarn() {
    assertThat(selectedConflictWarn)
        .as(
            "a conflict-point WARN must be selected first via 'a stale-version conflict WARN was"
                + " logged at the ... point'")
        .isNotNull();
    return selectedConflictWarn;
  }

  private Claim requireClaim() {
    UUID claimId = sharedPatchContext.getClaimId();
    assertThat(claimId).as("current claim id").isNotNull();
    return claimRepository
        .findById(claimId)
        .orElseThrow(() -> new AssertionError("Claim missing after PATCH: " + claimId));
  }

  private static List<String> warnMessages(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .filter(e -> e.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private String buildNonPricingPatch(long submittedVersion) {
    return "{\"version\":"
        + submittedVersion
        + ",\"amendment_requested_by\":\"PROVIDER\""
        + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
        + ",\"amendment_user_id\":\""
        + AMENDMENT_USER_ID
        + "\""
        + ",\"client_forename\":\""
        + AMENDMENT_PAYLOAD_MARKER_FORENAME
        + "\"}";
  }
}
