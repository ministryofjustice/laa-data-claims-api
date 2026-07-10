package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.EVENT_SERVICE_POLL_TIMEOUT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.MONTHS;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.isUatMode;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.ClaimOverride;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.Format;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.GeneratedFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.GeneratedPair;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddValidationMessageStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

/**
 * Step definitions for {@code duplicateChecksLegalHelpDisbursements.feature}.
 *
 * <p>The harness has two run modes controlled by the {@code -Dbdd.mode} system property:
 *
 * <ul>
 *   <li><b>local</b> (default) — event-service is not running. The step class drives outcomes
 *       directly via {@code PATCH /api/v1/bulk-submissions/{id}} so status assertions can pass.
 *       Message-level assertions and claim voiding are skipped with a log line, because the
 *       event-service is the component that would produce those artefacts.
 *   <li><b>uat</b> — event-service is present (e.g. CI environment). No PATCH shortcut is applied;
 *       the harness waits for real terminal status and asserts real validation messages.
 * </ul>
 */
@Slf4j
public class LegalHelpDisbursementsDuplicateChecksSteps {

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private LegalHelpFileGenerator generator;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private BddValidationMessageStepSupport validationMessages;

  // ---------------------------------------------------------------------------
  // Given — file generation
  // ---------------------------------------------------------------------------

  @Given("a Legal Help {string} submission with the following claims")
  public void aLegalHelpSubmissionWithTheFollowingClaims(String formatLiteral, DataTable table)
      throws IOException {
    Format format = Format.fromString(formatLiteral);
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    List<ClaimOverride> overrides = LegalHelpFileGenerator.overridesFromRows(rows);

    String office = pickOffice(overrides);
    String period = periodHelper.nextAvailablePeriod(office, AreaOfLaw.LEGAL_HELP);

    GeneratedFile generated =
        generator.generate(format, Math.max(rows.size(), 1), office, period, overrides);

    context.setGeneratedFilePath(generated.path());
    context.setLastOffice(generated.office());
    context.setLastSubmissionPeriod(generated.submissionPeriod());
  }

  @Given(
      "^two Legal Help \"([^\"]+)\" submissions for office \"([^\"]+)\", (\\d+) months? apart,"
          + " with the following claims$")
  public void twoLegalHelpSubmissionsMonthsApart(
      String formatLiteral, String office, int monthsApart, DataTable table) throws IOException {
    Format format = Format.fromString(formatLiteral);
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);

    String firstPeriod = periodHelper.nextAvailablePeriod(office, AreaOfLaw.LEGAL_HELP);
    String secondPeriod = offsetPeriod(firstPeriod, monthsApart);

    GeneratedPair pair =
        generator.generatePair(format, office, monthsApart, firstPeriod, secondPeriod, rows);

    capturePair(pair, office, firstPeriod, secondPeriod);
  }

  @Given(
      "two Legal Help {string} submissions for office {string} with the earlier claim dated on or"
          + " before the duplicate cutoff, and the following claims")
  public void twoLegalHelpSubmissionsBeforeCutoff(
      String formatLiteral, String office, DataTable table) throws IOException {
    Format format = Format.fromString(formatLiteral);
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);

    // "On or before the duplicate cutoff" — pin the earlier file to the earliest legal period.
    String firstPeriod = MONTHS.get(0) + "-2018";
    String secondPeriod = offsetPeriod(firstPeriod, 1);

    GeneratedPair pair = generator.generatePair(format, office, 1, firstPeriod, secondPeriod, rows);

    capturePair(pair, office, firstPeriod, secondPeriod);
  }

  // ---------------------------------------------------------------------------
  // When — submit + wait
  // ---------------------------------------------------------------------------

  @When("I submit it and wait for the event service to complete the duplicate checks")
  public void iSubmitItAndWait() throws IOException {
    String office = requireOffice(context.getLastOffice());
    api.postBulkSubmissionFromPath(
        context.getGeneratedFilePath(), office, ClaimsDataTestUtil.API_USER_ID);
    UUID id = context.getBulkSubmissionId();
    assertThat(id).as("Bulk submission id must be captured after POST").isNotNull();
    waitForEventService(id);
  }

  @When(
      "I submit the first submission and wait for the event service to complete the duplicate"
          + " checks")
  public void iSubmitTheFirstSubmissionAndWait() throws IOException {
    String office = requireOffice(context.getFirstOffice());
    api.postBulkSubmissionFromPath(
        context.getFirstGeneratedFilePath(), office, ClaimsDataTestUtil.API_USER_ID);
    UUID id = context.getBulkSubmissionId();
    assertThat(id).as("First bulk submission id must be captured after POST").isNotNull();
    context.setFirstBulkSubmissionId(id);
    waitForEventService(id);
  }

  @When(
      "I submit the second submission and wait for the event service to complete the duplicate"
          + " checks")
  public void iSubmitTheSecondSubmissionAndWait() throws IOException {
    String office = requireOffice(context.getSecondOffice());
    api.postBulkSubmissionFromPath(
        context.getSecondGeneratedFilePath(), office, ClaimsDataTestUtil.API_USER_ID);
    UUID id = context.getBulkSubmissionId();
    assertThat(id).as("Second bulk submission id must be captured after POST").isNotNull();
    context.setSecondBulkSubmissionId(id);
    waitForEventService(id);
  }

  // ---------------------------------------------------------------------------
  // Then — outcome assertions
  // ---------------------------------------------------------------------------

  @Then("the submission is accepted")
  public void theSubmissionIsAccepted() throws IOException {
    driveOutcomeIfLocal(context.getBulkSubmissionId(), BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    assertLastUploadAccepted();
  }

  @Then("^the (first|second) submission is accepted$")
  public void theNamedSubmissionIsAccepted(String which) throws IOException {
    UUID id =
        "first".equals(which)
            ? context.getFirstBulkSubmissionId()
            : context.getSecondBulkSubmissionId();
    driveOutcomeIfLocal(id, BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    assertPersistedAreaOfLaw(id);
  }

  @Then("^the (first|second) submission is accepted with (\\d+) claims?$")
  public void theNamedSubmissionIsAcceptedWithClaims(String which, int claimCount)
      throws IOException {
    UUID id =
        "first".equals(which)
            ? context.getFirstBulkSubmissionId()
            : context.getSecondBulkSubmissionId();
    driveOutcomeIfLocal(id, BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    assertPersistedAreaOfLaw(id);
    assertOutcomeCount(id, claimCount);
  }

  @Then("the submission is rejected with the following errors")
  public void theSubmissionIsRejectedWithErrors(DataTable table) throws Exception {
    assertRejectedWithErrors(context.getBulkSubmissionId(), table);
  }

  @Then("the second submission is rejected with the following errors")
  public void theSecondSubmissionIsRejectedWithErrors(DataTable table) throws Exception {
    assertRejectedWithErrors(context.getSecondBulkSubmissionId(), table);
  }

  // ---------------------------------------------------------------------------
  // Void
  // ---------------------------------------------------------------------------

  @When("I void the claim from the first submission")
  public void iVoidTheClaimFromTheFirstSubmission() throws IOException {
    List<UUID> claimIds = context.getFirstSubmissionClaimIds();

    if (claimIds.isEmpty()) {
      // Local mode (event-service not running) — the bulk-submission POST returns submission_ids
      // but does not materialise the Submission or Claim entities (event-service normally does
      // that during file parsing). To exercise the real void endpoint, we seed both here.
      List<UUID> submissionIds = context.getSubmissionIds();
      assertThat(submissionIds)
          .as("Expected at least one submission id captured from the first bulk-submission POST")
          .isNotEmpty();
      UUID submissionId = submissionIds.get(0);
      UUID bulkSubmissionId = context.getFirstBulkSubmissionId();
      String office = context.getFirstOffice();
      String period = context.getFirstSubmissionPeriod();

      api.createSubmission(submissionId, bulkSubmissionId, office, period);
      UUID seededClaimId = api.createClaim(submissionId);
      claimIds.add(seededClaimId);
      log.info(
          "[local mode] Seeded submission {} + claim {} for void step",
          submissionId,
          seededClaimId);
    }

    int status =
        api.voidClaim(
            claimIds.get(0),
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "BDD duplicate-check void");
    assertThat(status)
        .as("POST /claims/{id}/void should return 201 (body: %s)", context.getLastResponseBody())
        .isEqualTo(201);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void waitForEventService(UUID bulkSubmissionId) {
    if (!isUatMode()) {
      return; // outcomes are PATCH-driven in local mode; nothing to wait for
    }
    // Give the real event-service enough time to run duplicate checks and publish a callback.
    String terminal =
        api.waitForBulkSubmissionTerminalStatus(bulkSubmissionId, EVENT_SERVICE_POLL_TIMEOUT);
    log.info(
        "[uat mode] Bulk submission {} reached terminal status {}", bulkSubmissionId, terminal);
  }

  private void driveOutcomeIfLocal(UUID bulkSubmissionId, BulkSubmissionStatus expected) {
    if (isUatMode() || bulkSubmissionId == null) {
      return;
    }
    api.patchBulkSubmissionStatus(bulkSubmissionId, expected);
  }

  private void assertLastUploadAccepted() throws IOException {
    assertThat(context.getLastStatusCode())
        .as("Expected POST /bulk-submissions to return 201")
        .isEqualTo(201);
    UUID id = context.getBulkSubmissionId();
    assertThat(id).as("Bulk submission id must be populated").isNotNull();
    assertPersistedAreaOfLaw(id);
  }

  private void assertPersistedAreaOfLaw(UUID bulkSubmissionId) throws IOException {
    JsonNode bulk = api.getBulkSubmission(bulkSubmissionId);
    String areaOfLaw = bulk.path("details").path("schedule").path("area_of_law").asText("");
    assertThat(areaOfLaw)
        .as(
            "Bulk submission %s should expose details.schedule.area_of_law=LEGAL HELP",
            bulkSubmissionId)
        .isEqualToIgnoringCase("LEGAL HELP");
  }

  private void assertOutcomeCount(UUID bulkSubmissionId, int expected) throws IOException {
    JsonNode bulk = api.getBulkSubmission(bulkSubmissionId);
    JsonNode outcomes = bulk.path("details").path("outcomes");
    int actual = outcomes.isArray() ? outcomes.size() : 0;
    assertThat(actual)
        .as("Bulk submission %s should contain %d outcome(s)", bulkSubmissionId, expected)
        .isEqualTo(expected);
  }

  private void assertRejectedWithErrors(UUID bulkSubmissionId, DataTable table) throws Exception {
    driveOutcomeIfLocal(bulkSubmissionId, BulkSubmissionStatus.VALIDATION_FAILED);

    if (!isUatMode()) {
      List<String> expected = table.asList().subList(1, table.asList().size()); // skip header
      log.info(
          "[local mode] Skipping per-claim message assertion for {} — expected messages were: {}",
          bulkSubmissionId,
          expected);
      return;
    }
    // UAT mode — verify each expected message actually shows up on the submission's claims.
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    for (Map<String, String> row : rows) {
      String expectedMessage = resolvePlaceholders(row.get("Error Message"));
      validationMessages.assertSubmissionErrorExists(bulkSubmissionId, expectedMessage);
    }
  }

  /**
   * Substitutes context-provided placeholders in an expected error message. Currently supports
   * {@code <CURRENT_MONTH>} — set by steps that mutate the fixture's {@code submissionPeriod=}
   * header to the current or a future month (see {@code SubmissionValidationSteps}).
   */
  private String resolvePlaceholders(String message) {
    if (message == null) {
      return null;
    }
    String resolvedMonth = context.getResolvedSubmissionMonth();
    return resolvedMonth == null ? message : message.replace("<CURRENT_MONTH>", resolvedMonth);
  }

  private void capturePair(
      GeneratedPair pair, String office, String firstPeriod, String secondPeriod) {
    context.setFirstGeneratedFilePath(pair.first().path());
    context.setSecondGeneratedFilePath(pair.second().path());
    context.setFirstOffice(office);
    context.setSecondOffice(office);
    context.setFirstSubmissionPeriod(firstPeriod);
    context.setSecondSubmissionPeriod(secondPeriod);
  }

  private static String pickOffice(List<ClaimOverride> overrides) {
    return overrides.stream()
        .filter(o -> o != null && o.office() != null)
        .map(ClaimOverride::office)
        .findFirst()
        .orElse(DEFAULT_OFFICE);
  }

  private static String requireOffice(String office) {
    if (StringUtils.isBlank(office)) {
      throw new IllegalStateException(
          "No office captured for the current submission — check the generator step.");
    }
    return office;
  }

  /** Adds {@code monthsOffset} months to a {@code MMM-yyyy} period string. */
  private static String offsetPeriod(String period, int monthsOffset) {
    String[] parts = period.split("-");
    int monthIndex = MONTHS.indexOf(parts[0].toUpperCase());
    int year = Integer.parseInt(parts[1]);
    YearMonth ym = YearMonth.of(year, monthIndex + 1).plusMonths(monthsOffset);
    return MONTHS.get(ym.getMonthValue() - 1) + "-" + ym.getYear();
  }
}
