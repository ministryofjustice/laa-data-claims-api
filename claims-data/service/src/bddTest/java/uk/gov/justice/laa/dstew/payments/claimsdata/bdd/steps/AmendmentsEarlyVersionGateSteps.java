package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimVersionValidationStep;

/**
 * Step glue for {@code amendmentsEarlyVersionGate.feature} — DSTEW-1752.
 *
 * <p>Proves the EARLY optimistic-concurrency gate on the amendment PATCH path — the {@link
 * ClaimVersionValidationStep} (Phase 2) that compares the submitted {@code version} with the
 * freshly read before-state {@code claim.version} and short-circuits with HTTP 409 {@code
 * CLAIM_VERSION_CONFLICT} BEFORE any PDA/FSP call. Complements the commit-time guard covered by
 * {@code amendmentsFinalSaveGuard.feature} (DSTEW-1753).
 *
 * <h2>Why this class adds its own seeding steps</h2>
 *
 * <p>The shared harness step {@code a fresh amendable claim ... at version N} (owned by {@link
 * AmendmentHarnessCommonSteps}) builds the submit payload with the SAME version it seeds, so the
 * early gate always matches — perfect for the happy-path scenario, which reuses it verbatim. The
 * stale-version scenarios need the stored version and the submitted version to DIFFER, so this
 * class adds:
 *
 * <ul>
 *   <li>{@code a stored amendable claim at version N} — seeds a fresh amendable claim (version 0)
 *       then forces {@code claim.version} to N via direct SQL (reliable, unlike re-saving through
 *       JPA's {@code @Version}), and records the N / baseline-CFD-count baselines on the shared
 *       context so the reused "no persistence" / "claim.version equals" Thens read a real baseline.
 *   <li>{@code the submitted amendment carries the stale claim version M} — rebuilds the shared
 *       patch JSON with a submitted version M that differs from the stored N.
 * </ul>
 *
 * <p>All outcome assertions (409 + code, RFC 9457 envelope, no PDA/FSP call, no persistence,
 * claim.version/is_amended) are REUSED from {@link AmendmentHarnessCommonSteps} and {@link
 * AmendmentsFinalSaveGuardSteps} via Cucumber's cross-class step registry — only the {@code
 * initial_check} WARN-log capture + assertions are local, because the early gate logs from {@link
 * ClaimVersionValidationStep} whereas the final-save guard logs from a different class.
 *
 * <p>Every step body is wrapped in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step} per the
 * project-wide step-failure-reporting standing rule.
 */
@Slf4j
public class AmendmentsEarlyVersionGateSteps {

  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-175200000001";
  // Marker value embedded in the non-pricing payload — asserted ABSENT from the WARN log to prove
  // the structured diagnostic never leaks amendment payload field values.
  private static final String AMENDMENT_PAYLOAD_MARKER_FORENAME = "Harness-Canary";

  @Autowired private AmendableClaimFixture fixture;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private JdbcClient jdbcClient;

  private ListAppender<ILoggingEvent> gateLogAppender;
  private Logger gateStepLogger;

  // ---------------------------------------------------------------------------
  // Lifecycle — attach + detach the WARN capture ONLY for @dstew-1752 scenarios
  // so we don't pollute unrelated tests with an extra appender.
  // ---------------------------------------------------------------------------

  @Before("@dstew-1752")
  public void attachGateLogCapture() {
    gateStepLogger = (Logger) LoggerFactory.getLogger(ClaimVersionValidationStep.class);
    gateLogAppender = new ListAppender<>();
    gateLogAppender.start();
    gateStepLogger.addAppender(gateLogAppender);
  }

  @After("@dstew-1752")
  public void detachGateLogCapture() {
    if (gateStepLogger != null && gateLogAppender != null) {
      gateStepLogger.detachAppender(gateLogAppender);
    }
  }

  // ---------------------------------------------------------------------------
  // Given — seed a claim whose STORED version we control independently of the
  // submitted version.
  // ---------------------------------------------------------------------------

  @Given("a stored amendable claim at version {long}")
  public void aStoredAmendableClaimAtVersion(long storedVersion) {
    step(
        "seed a fresh amendable Legal Help claim then force claim.version = " + storedVersion,
        () -> {
          AmendableClaimFixture.Seeded seeded = fixture.legalHelpValid().withVersion(0).seed();
          forceClaimVersion(seeded.claimId(), storedVersion);

          sharedPatchContext.setSubmissionId(seeded.submissionId());
          sharedPatchContext.setClaimId(seeded.claimId());
          // Default the payload to the stored version; the stale-version Given below overrides it.
          sharedPatchContext.setPatchJson(buildNonPricingPatch(storedVersion));
          sharedPatchContext.setBaselineClaimVersion(storedVersion);
          sharedPatchContext.setBaselineCfdCount(countCfd(seeded.claimId()));
          log.info(
              "[DSTEW-1752] Seeded claim {} with stored version forced to {}",
              seeded.claimId(),
              storedVersion);
        });
  }

  @Given("the submitted amendment carries the stale claim version {long}")
  public void theSubmittedAmendmentCarriesTheStaleClaimVersion(long submittedVersion) {
    step(
        "rebuild the shared patch JSON so the submitted version ("
            + submittedVersion
            + ") differs"
            + " from the stored version",
        () -> {
          assertThat(sharedPatchContext.getClaimId())
              .as(
                  "a stored amendable claim must be seeded before setting a stale submitted version")
              .isNotNull();
          sharedPatchContext.setPatchJson(buildNonPricingPatch(submittedVersion));
        });
  }

  // ---------------------------------------------------------------------------
  // Then — initial_check WARN log assertions (early gate logs from
  // ClaimVersionValidationStep).
  // ---------------------------------------------------------------------------

  @Then("a WARN log entry from the early version gate was captured")
  public void aWarnLogEntryFromTheEarlyVersionGateWasCaptured() {
    step(
        "assert the log capture attached to ClaimVersionValidationStep recorded at least one WARN"
            + " entry — proves the early gate detected the mismatch and emitted its structured"
            + " diagnostic",
        () ->
            assertThat(warnEntries())
                .as("WARN log entries captured on ClaimVersionValidationStep")
                .isNotEmpty());
  }

  @Then("the early-gate WARN log contains {string}")
  public void theEarlyGateWarnLogContains(String needle) {
    step(
        "assert at least one captured WARN entry individually contains \""
            + needle
            + "\" — avoids false positives from tokens spread across separate log lines",
        () ->
            assertThat(warnMessages())
                .as("captured WARN entries on ClaimVersionValidationStep")
                .anyMatch(msg -> msg.contains(needle)));
  }

  @Then("the early-gate WARN log contains the current claim id")
  public void theEarlyGateWarnLogContainsTheCurrentClaimId() {
    step(
        "assert at least one captured WARN entry individually references the current claimId",
        () -> {
          UUID claimId = sharedPatchContext.getClaimId();
          assertThat(claimId).as("current claim id").isNotNull();
          String token = "claimId=" + claimId;
          assertThat(warnMessages())
              .as("captured WARN entries on ClaimVersionValidationStep")
              .anyMatch(msg -> msg.contains(token));
        });
  }

  @Then("the early-gate WARN log does not carry any amendment payload field values")
  public void theEarlyGateWarnLogDoesNotCarryAnyAmendmentPayloadFieldValues() {
    step(
        "assert no captured WARN entry contains the amendment payload's field-value markers —"
            + " proves the gate's structured log carries only the whitelisted safe fields",
        () -> {
          String warnBody = allWarnFormatted();
          assertThat(warnBody.toLowerCase(Locale.ROOT))
              .as("WARN log must not carry the client_forename payload value")
              .doesNotContain(AMENDMENT_PAYLOAD_MARKER_FORENAME.toLowerCase(Locale.ROOT));
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

  private void forceClaimVersion(UUID claimId, long version) {
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

  private long countCfd(UUID claimId) {
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
        + ",\"client_forename\":\""
        + AMENDMENT_PAYLOAD_MARKER_FORENAME
        + "\"}";
  }

  private List<ILoggingEvent> warnEntries() {
    return gateLogAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
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
}
