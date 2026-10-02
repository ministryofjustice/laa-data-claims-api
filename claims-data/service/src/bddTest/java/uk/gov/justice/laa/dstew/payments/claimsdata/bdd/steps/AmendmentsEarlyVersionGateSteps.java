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
import org.mockserver.verify.VerificationTimes;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;
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
  // Marker value embedded in the stale payload — asserted ABSENT from the WARN log to prove
  // the structured diagnostic never leaks amendment payload field values.
  private static final String AMENDMENT_PAYLOAD_MARKER_FORENAME = "Harness-Canary";
  // Conflict-specific downstream canary. case_start_date is BOTH PDA-impacting
  // (PdaRequestField.CASE_START_DATE) and pricing-impacting (FeeSchemeRequestField.START_DATE),
  // so a version-MATCHING submit of this payload WOULD drive PDA + FSP (fee-details and
  // fee-calculation). Embedding it in the STALE payload makes DS1752_4's "no external call"
  // assertions meaningful: they fail if the early gate were moved after external validation.
  // The fixture baseline caseStartDate is 01/07/2025, so 04/08/2025 is a genuine change.
  private static final String CANARY_CASE_START_DATE = "04/08/2025";

  @Autowired private AmendableClaimFixture fixture;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private BddMockServerSupport mock;

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
          sharedPatchContext.setPatchJson(buildStalePatch(storedVersion));
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
          sharedPatchContext.setPatchJson(buildStalePatch(submittedVersion));
        });
  }

  // ---------------------------------------------------------------------------
  // Then — initial_check WARN log assertions (early gate logs from
  // ClaimVersionValidationStep).
  // ---------------------------------------------------------------------------

  @Then("no outbound FSP fee-details call was made")
  public void noOutboundFspFeeDetailsCallWasMade() {
    step(
        "verify MockServer recorded no outbound FSP /fee-details call — the stale payload changes"
            + " case_start_date (a fee-details/fee-calculation driver), so an empty fee-details"
            + " journal proves the early gate short-circuited before FSP fee-scheme resolution",
        () -> mock.verifyFeeDetailsCalled(VerificationTimes.never()));
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
        "assert the SINGLE structured conflict WARN entry for the current claim contains \""
            + needle
            + "\" — proves one entry carries the complete contract, not tokens spread across"
            + " separate log lines",
        () ->
            assertThat(conflictWarnEntryForCurrentClaim())
                .as("the structured conflict WARN entry for the current claim")
                .contains(needle));
  }

  @Then("the early-gate WARN log contains the current claim id")
  public void theEarlyGateWarnLogContainsTheCurrentClaimId() {
    step(
        "assert exactly one captured WARN entry references the current claimId (the structured"
            + " conflict entry under test)",
        () -> {
          UUID claimId = sharedPatchContext.getClaimId();
          assertThat(claimId).as("current claim id").isNotNull();
          // conflictWarnEntryForCurrentClaim() asserts exactly one entry carrying claimId=<id>.
          assertThat(conflictWarnEntryForCurrentClaim())
              .as("the structured conflict WARN entry references the current claim")
              .contains("claimId=" + claimId);
        });
  }

  @Then("the early-gate WARN log does not carry any amendment payload field values")
  public void theEarlyGateWarnLogDoesNotCarryAnyAmendmentPayloadFieldValues() {
    step(
        "assert the structured conflict WARN entry contains none of the amendment payload's"
            + " field-value markers — proves it carries only the whitelisted safe fields",
        () -> {
          String warnEntry = conflictWarnEntryForCurrentClaim();
          assertThat(warnEntry.toLowerCase(Locale.ROOT))
              .as("WARN entry must not carry the client_forename payload value")
              .doesNotContain(AMENDMENT_PAYLOAD_MARKER_FORENAME.toLowerCase(Locale.ROOT));
          assertThat(warnEntry)
              .as("WARN entry must not carry the case_start_date canary payload value")
              .doesNotContain(CANARY_CASE_START_DATE);
          assertThat(warnEntry)
              .as("WARN entry must not carry the amendment_reason_code payload literal")
              .doesNotContain("PROVIDER_ERROR");
          assertThat(warnEntry)
              .as("WARN entry must not carry the amendment_requested_by payload literal")
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

  private String buildStalePatch(long submittedVersion) {
    // Carries the case_start_date downstream canary (PDA- and FSP-impacting) so the early-gate
    // ordering is genuinely provable, plus a client_forename marker used only to prove no payload
    // value leaks into the structured WARN log.
    return "{\"version\":"
        + submittedVersion
        + ",\"amendment_requested_by\":\"PROVIDER\""
        + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
        + ",\"amendment_user_id\":\""
        + AMENDMENT_USER_ID
        + "\""
        + ",\"case_start_date\":\""
        + CANARY_CASE_START_DATE
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

  /**
   * Returns the single structured conflict WARN entry for the claim under test, asserting exactly
   * one such entry exists. Scoping to {@code claimId=<id>} and requiring a unique match proves ONE
   * log line carries the complete diagnostic contract, rather than letting separate entries each
   * satisfy a different token.
   */
  private String conflictWarnEntryForCurrentClaim() {
    UUID claimId = sharedPatchContext.getClaimId();
    assertThat(claimId).as("current claim id must be seeded").isNotNull();
    String token = "claimId=" + claimId;
    List<String> matching = warnMessages().stream().filter(msg -> msg.contains(token)).toList();
    assertThat(matching)
        .as("exactly one structured conflict WARN entry for claim %s", claimId)
        .hasSize(1);
    return matching.get(0);
  }
}
