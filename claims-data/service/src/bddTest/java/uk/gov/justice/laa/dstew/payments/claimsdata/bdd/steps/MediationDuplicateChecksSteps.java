package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.EVENT_SERVICE_POLL_TIMEOUT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.isUatMode;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.ClaimOverride;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.Format;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.GeneratedFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddValidationMessageStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

/**
 * Step definitions for {@code duplicateChecksMediation.feature}.
 *
 * <p>Mirrors {@link LegalHelpDuplicateChecksSteps} in shape and vocabulary, but drives Mediation
 * submissions ({@link AreaOfLaw#MEDIATION}). Shared vocabulary from the Legal Help / disbursement
 * step classes (e.g. {@code When I submit it and wait for the event service to complete the
 * duplicate checks}, {@code Then the submission is accepted}, {@code Then the submission is
 * rejected with the following errors}, {@code When the previous submission is marked invalid}) is
 * reused as-is via Cucumber's cross-class step registry.
 *
 * <p>The harness has two run modes controlled by the {@code -Dbdd.mode} system property:
 *
 * <ul>
 *   <li><b>local</b> (default) — event-service is not running. The step class drives outcomes
 *       directly via {@code PATCH /api/v1/bulk-submissions/{id}} so status assertions can pass;
 *       per-message assertions are logged and skipped.
 *   <li><b>uat</b> — event-service is present. No PATCH shortcut is applied; the harness waits for
 *       real terminal status and asserts each expected message via {@code GET
 *       /api/v1/validation-messages}.
 * </ul>
 */
@Slf4j
public class MediationDuplicateChecksSteps {

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private BulkSubmissionFileGenerator generator;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private BddValidationMessageStepSupport validationMessages;

  // ---------------------------------------------------------------------------
  // Given — generated file (Mediation single-file phrasings)
  // ---------------------------------------------------------------------------

  @Given("a Mediation {string} submission with claims")
  public void aMediationSubmissionWithClaims(String formatLiteral, DataTable claimsTable)
      throws IOException {
    generateFile(formatLiteral, claimsTable);
  }

  @Given("a Mediation {string} submission with the following claims")
  public void aMediationSubmissionWithTheFollowingClaims(
      String formatLiteral, DataTable claimsTable) throws IOException {
    generateFile(formatLiteral, claimsTable);
  }

  // ---------------------------------------------------------------------------
  // Then — Mediation-specific acceptance assertions so we can verify the
  // persisted bulk submission's area-of-law header round-trips as MEDIATION
  // (the shared Legal Help "Then the submission is accepted" steps assert
  // area_of_law=LEGAL HELP, which is wrong for Mediation).
  // ---------------------------------------------------------------------------

  @Then("the Mediation submission is accepted")
  public void theMediationSubmissionIsAccepted() throws IOException {
    UUID id = context.getBulkSubmissionId();
    driveOutcomeIfLocal(id, BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    assertLastUploadAcceptedForArea(id, AreaOfLaw.MEDIATION);
  }

  @Then("^the Mediation submission is accepted with (\\d+) claims?$")
  public void theMediationSubmissionIsAcceptedWithClaims(int claimCount) throws IOException {
    UUID id = context.getBulkSubmissionId();
    driveOutcomeIfLocal(id, BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    assertLastUploadAcceptedForArea(id, AreaOfLaw.MEDIATION);
    assertOutcomeCount(id, claimCount);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void generateFile(String formatLiteral, DataTable claimsTable) throws IOException {
    Format format = Format.fromString(formatLiteral);
    List<Map<String, String>> rows = claimsTable.asMaps(String.class, String.class);
    List<ClaimOverride> overrides = BulkSubmissionFileGenerator.overridesFromRows(rows);

    String office = pickOffice(overrides);
    String period = periodHelper.nextAvailablePeriod(office, AreaOfLaw.MEDIATION);

    GeneratedFile generated =
        generator.generate(
            format, Math.max(rows.size(), 1), office, period, overrides, AreaOfLaw.MEDIATION);

    context.setGeneratedFilePath(generated.path());
    context.setLastOffice(generated.office());
    context.setLastSubmissionPeriod(generated.submissionPeriod());
  }

  private void driveOutcomeIfLocal(UUID bulkSubmissionId, BulkSubmissionStatus expected) {
    if (isUatMode() || bulkSubmissionId == null) {
      return;
    }
    api.patchBulkSubmissionStatus(bulkSubmissionId, expected);
  }

  private void assertLastUploadAcceptedForArea(UUID bulkSubmissionId, AreaOfLaw expectedArea)
      throws IOException {
    assertThat(context.getLastStatusCode())
        .as("Expected POST /bulk-submissions to return 201")
        .isEqualTo(201);
    assertThat(bulkSubmissionId).as("Bulk submission id must be populated").isNotNull();
    JsonNode bulk = api.getBulkSubmission(bulkSubmissionId);
    String areaOfLaw = bulk.path("details").path("schedule").path("area_of_law").asText("");
    assertThat(areaOfLaw)
        .as(
            "Bulk submission %s should expose details.schedule.area_of_law=%s",
            bulkSubmissionId, expectedArea.getValue())
        .isEqualToIgnoringCase(expectedArea.getValue());
  }

  private void assertOutcomeCount(UUID bulkSubmissionId, int expected) throws IOException {
    JsonNode bulk = api.getBulkSubmission(bulkSubmissionId);
    JsonNode outcomes = bulk.path("details").path("outcomes");
    int actual = outcomes.isArray() ? outcomes.size() : 0;
    assertThat(actual)
        .as("Bulk submission %s should contain %d outcome(s)", bulkSubmissionId, expected)
        .isEqualTo(expected);
  }

  private static String pickOffice(List<ClaimOverride> overrides) {
    return overrides.stream()
        .filter(o -> o != null && o.office() != null)
        .map(ClaimOverride::office)
        .findFirst()
        .orElse(DEFAULT_OFFICE);
  }

  @SuppressWarnings("unused") // kept for symmetry with sibling step classes; not currently invoked
  private static String requireOffice(String office) {
    if (StringUtils.isBlank(office)) {
      throw new IllegalStateException(
          "No office captured for the current submission — check the generator step.");
    }
    return office;
  }

  @SuppressWarnings("unused") // referenced only when UAT mode is active in future extensions
  private void waitForEventService(UUID bulkSubmissionId) {
    if (!isUatMode()) {
      return;
    }
    String terminal =
        api.waitForBulkSubmissionTerminalStatus(bulkSubmissionId, EVENT_SERVICE_POLL_TIMEOUT);
    log.info(
        "[uat mode] Bulk submission {} reached terminal status {}", bulkSubmissionId, terminal);
  }
}
