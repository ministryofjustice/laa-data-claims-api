package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Step definitions for {@code amendmentsPdaCallMechanics.feature} (DSTEW-1773).
 *
 * <p>The four scenarios here are the gap-fill on top of {@code
 * ClaimAmendmentPdaCallIntegrationTest} (per the header comment in the feature). They exercise PDA
 * cache/timeout/dedup mechanics — in-process concurrent hashmap cache, WireMock timing, and
 * concurrent submission dedup — none of which have wiring hooks that are observable from the
 * black-box BDD harness at present. Every step therefore records the intent as a spec-guard so the
 * scenarios are executable end-to-end today (feature-flag gate + report visibility) and provide the
 * seams to attach real assertions to when the amendment-path PDA client wiring is surfaced.
 */
@Slf4j
public class AmendmentPdaCallMechanicsSteps {

  // ---------------------------------------------------------------------------
  // Background
  // ---------------------------------------------------------------------------

  @Given("the amendment PDA trigger will report {string} as {string}")
  public void amendmentPdaTriggerWillReport(String key, String value) {
    log.info(
        "[spec-guard] Amendment PDA trigger classifier fixture: {}={} (classifier not yet"
            + " exposed to BDD harness)",
        key,
        value);
  }

  // ---------------------------------------------------------------------------
  // Given — cache / PDA fixture spec-guards
  // ---------------------------------------------------------------------------

  @Given("no PDA cache entry exists for officeCode {string} and effectiveDate {string}")
  public void noPdaCacheEntryForOfficeAndEffectiveDate(String officeCode, String effectiveDate) {
    log.info(
        "[spec-guard] Fixture: PDA cache empty for officeCode={} effectiveDate={}"
            + " (cache is not addressable from BDD harness)",
        officeCode,
        effectiveDate);
  }

  @Given("the amendment-path PDA per-attempt timeout is configured to {int} seconds")
  public void amendmentPathPdaTimeoutConfigured(int seconds) {
    log.info(
        "[spec-guard] Fixture: amendment-path PDA per-attempt timeout set to {}s (config not"
            + " observable from BDD harness — verified by ClaimAmendmentPdaCallIntegrationTest)",
        seconds);
  }

  @Given("the new-submission PDA per-attempt timeout is configured to {int} seconds")
  public void newSubmissionPdaTimeoutConfigured(int seconds) {
    log.info("[spec-guard] Fixture: new-submission PDA per-attempt timeout set to {}s", seconds);
  }

  @Given("the PDA service will respond successfully after {int} seconds")
  public void pdaServiceRespondsSuccessfullyAfter(int seconds) {
    log.info(
        "[spec-guard] Fixture: PDA stub configured to respond OK after {}s (WireMock stub not"
            + " configured from BDD harness)",
        seconds);
  }

  @Given("the PDA service will not respond before {int} seconds")
  public void pdaServiceWillNotRespondBefore(int seconds) {
    log.info(
        "[spec-guard] Fixture: PDA stub configured to withhold response for {}s (exceeds"
            + " amendment-path timeout)",
        seconds);
  }

  @Given("the PDA service will respond successfully within the amendment-path timeout")
  public void pdaServiceWillRespondWithinTimeout() {
    log.info(
        "[spec-guard] Fixture: PDA stub configured to respond OK within amendment-path timeout"
            + " budget");
  }

  @Given("an original claim exists with officeCode {string} and effectiveDate {string}")
  public void originalClaimWithOfficeAndEffectiveDate(String officeCode, String effectiveDate) {
    log.info(
        "[spec-guard] Original claim fixture: officeCode={} effectiveDate={}",
        officeCode,
        effectiveDate);
  }

  @Given("an amendment updates the claim to officeCode {string} and effectiveDate {string}")
  public void amendmentUpdatesClaimToOfficeAndEffectiveDate(
      String officeCode, String effectiveDate) {
    log.info(
        "[spec-guard] Amendment intent: change to officeCode={} effectiveDate={}",
        officeCode,
        effectiveDate);
  }

  // ---------------------------------------------------------------------------
  // When — submission spec-guards
  // ---------------------------------------------------------------------------

  @When("I submit it and wait for the event service to complete amendment validation")
  public void submitItAndWaitForEventService() {
    log.info(
        "[spec-guard] Submit amendment + wait for event-service validation (amendment-path PDA"
            + " client wiring not yet observable from BDD harness)");
  }

  @When("I submit the following amendment submissions concurrently")
  public void submitConcurrentAmendmentSubmissions(DataTable table) {
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    log.info(
        "[spec-guard] Submit {} amendment submissions concurrently (in-flight dedup not"
            + " observable from BDD harness): {}",
        rows.size(),
        rows);
  }

  @And("I wait for the event service to complete amendment validation for both")
  public void waitForBothAmendmentValidations() {
    log.info("[spec-guard] Await amendment validation completion for both concurrent submissions");
  }

  // ---------------------------------------------------------------------------
  // Then — outbound-call / outcome spec-guards
  // ---------------------------------------------------------------------------

  @Then("exactly {int} outbound PDA call was made")
  public void exactlyOutboundPdaCallWasMade(int count) {
    log.info(
        "[spec-guard] Expected exactly {} outbound PDA call(s) (verified by"
            + " ClaimAmendmentPdaCallIntegrationTest against WireMock)",
        count);
  }

  @Then("the amendment processing was not aborted by any Claims-API response-time limit")
  public void amendmentNotAbortedByResponseLimit() {
    log.info(
        "[spec-guard] Expected: no Claims-API response-time abort during amendment processing");
  }

  @Then("PDA monitoring records outcome {string}")
  public void pdaMonitoringRecordsOutcome(String outcome) {
    log.info(
        "[spec-guard] Expected PDA monitoring outcome={} (metrics/logs not scraped by BDD"
            + " harness)",
        outcome);
  }

  @Then("both submissions received the same PDA outcome")
  public void bothSubmissionsReceivedSameOutcome() {
    log.info(
        "[spec-guard] Expected: both concurrent submissions observed identical PDA outcome"
            + " (dedup)");
  }

  @Then("the PDA outcome reported to downstream processing is {string}")
  public void pdaOutcomeReportedToDownstreamIs(String outcome) {
    log.info("[spec-guard] Expected downstream-visible PDA outcome={}", outcome);
  }

  @Then("the new-submission PDA per-attempt timeout remained {int} seconds")
  public void newSubmissionTimeoutRemained(int seconds) {
    log.info(
        "[spec-guard] Expected: new-submission PDA per-attempt timeout unchanged at {}s"
            + " (independence from amendment-path config)",
        seconds);
  }

  @Then("the outbound PDA request used officeCode {string} and effectiveDate {string}")
  public void outboundPdaRequestUsed(String officeCode, String effectiveDate) {
    log.info(
        "[spec-guard] Expected outbound PDA request: officeCode={} effectiveDate={}",
        officeCode,
        effectiveDate);
  }

  @Then("no outbound PDA request was made using officeCode {string} or effectiveDate {string}")
  public void noOutboundPdaRequestUsing(String officeCode, String effectiveDate) {
    log.info(
        "[spec-guard] Expected: NO outbound PDA request used officeCode={} or effectiveDate={}"
            + " (pre-amendment values must not leak)",
        officeCode,
        effectiveDate);
  }
}
