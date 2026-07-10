package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.isUatMode;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.ClaimOverride;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.Format;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.GeneratedFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

/**
 * Step definitions for {@code duplicateChecksLegalHelp.feature}.
 *
 * <p>Consolidates what used to live across {@code LegalHelpDuplicateChecksSteps} (no-op scaffold),
 * {@code LegacyDuplicateChecksCompatibilitySteps} and {@code BulkSubmissionApiSteps} into a single
 * class. Steps shared with the disbursement feature (e.g. {@code Given a Legal Help "csv"
 * submission with the following claims}) live in {@link LegalHelpDisbursementsDuplicateChecksSteps}
 * — Cucumber picks them up regardless of which class declares them, so this class only owns the
 * ones DCLH needs uniquely.
 *
 * <p>The harness has two run modes controlled by the {@code -Dbdd.mode} system property:
 *
 * <ul>
 *   <li><b>local</b> (default) — event-service is not running. The step class drives outcomes
 *       directly via {@code PATCH /api/v1/bulk-submissions/{id}} so status assertions can pass.
 *   <li><b>uat</b> — event-service is present. No PATCH shortcut is applied; the harness waits for
 *       real terminal status.
 * </ul>
 */
@Slf4j
public class LegalHelpDuplicateChecksSteps {

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private LegalHelpFileGenerator generator;
  @Autowired private SubmissionPeriodHelper periodHelper;

  // ---------------------------------------------------------------------------
  // Given — static-fixture file (smoke scenarios)
  // ---------------------------------------------------------------------------

  @Given("a Legal Help submission from file {string} for office {string}")
  public void aLegalHelpSubmissionFromFileForOffice(String classpathFile, String office)
      throws IOException {
    ClassPathResource resource = new ClassPathResource(classpathFile);
    String filename = resource.getFilename() != null ? resource.getFilename() : "bdd-fixture";
    Path tempPath = Files.createTempFile("bdd-fixture-", "-" + filename);
    // Best-effort cleanup so long BDD runs don't leave copies of the classpath fixture in /tmp.
    tempPath.toFile().deleteOnExit();
    try (var in = resource.getInputStream()) {
      Files.write(tempPath, in.readAllBytes());
    }
    context.setGeneratedFilePath(tempPath);
    context.setLastOffice(office);
  }

  // ---------------------------------------------------------------------------
  // Given — generated file (DCLH-specific single-file wording, kept alongside
  // the disbursement feature's shared vocabulary).
  // ---------------------------------------------------------------------------

  @Given("a Legal Help {string} submission with claims")
  public void aLegalHelpFormatSubmissionWithClaims(String formatLiteral, DataTable claimsTable)
      throws IOException {
    generateFile(formatLiteral, claimsTable);
  }

  // ---------------------------------------------------------------------------
  // When — submit (both variants: with wait and without wait)
  // ---------------------------------------------------------------------------

  @When("I submit it")
  public void iSubmitIt() throws IOException {
    uploadCurrentFile();
  }

  @When("I submit it again")
  public void iSubmitItAgain() throws IOException {
    uploadCurrentFile();
  }

  // ---------------------------------------------------------------------------
  // When — DCLH-specific: mark the previous submission invalid before the next.
  // ---------------------------------------------------------------------------

  @When("the previous submission is marked invalid")
  public void thePreviousSubmissionIsMarkedInvalid() {
    UUID bulkId = context.getBulkSubmissionId();
    assertThat(bulkId)
        .as("A bulk submission must already exist before it can be marked invalid")
        .isNotNull();
    api.markBulkSubmissionAsInvalid(bulkId);
  }

  // ---------------------------------------------------------------------------
  // Then — outcome assertions specific to single-submission DCLH scenarios.
  // (First/second submission variants live in the disbursement steps and are
  //  reused via Cucumber's shared step registry.)
  // ---------------------------------------------------------------------------

  @Then("^the submission is accepted with (\\d+) claims?$")
  public void theSubmissionIsAcceptedWithClaims(int claimCount) throws IOException {
    UUID id = context.getBulkSubmissionId();
    driveOutcomeIfLocal(id, BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    assertLastUploadAccepted();
    assertOutcomeCount(id, claimCount);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void generateFile(String formatLiteral, DataTable claimsTable) throws IOException {
    Format format = Format.fromString(formatLiteral);
    List<Map<String, String>> rows = claimsTable.asMaps(String.class, String.class);
    List<ClaimOverride> overrides = LegalHelpFileGenerator.overridesFromRows(rows);

    String office = pickOffice(overrides);
    String period = periodHelper.nextAvailablePeriod(office, AreaOfLaw.LEGAL_HELP);

    GeneratedFile generated =
        generator.generate(format, Math.max(rows.size(), 1), office, period, overrides);

    context.setGeneratedFilePath(generated.path());
    context.setLastOffice(generated.office());
    context.setLastSubmissionPeriod(generated.submissionPeriod());
  }

  private void uploadCurrentFile() throws IOException {
    Path file = context.getGeneratedFilePath();
    assertThat(file).as("A file must be prepared before it can be submitted").isNotNull();
    String office = context.getLastOffice() != null ? context.getLastOffice() : DEFAULT_OFFICE;
    api.postBulkSubmissionFromPath(file, office, ClaimsDataTestUtil.API_USER_ID);
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
    JsonNode bulk = api.getBulkSubmission(id);
    String areaOfLaw = bulk.path("details").path("schedule").path("area_of_law").asText("");
    assertThat(areaOfLaw)
        .as("Bulk submission %s should expose details.schedule.area_of_law=LEGAL HELP", id)
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

  private static String pickOffice(List<ClaimOverride> overrides) {
    return overrides.stream()
        .filter(o -> o != null && o.office() != null)
        .map(ClaimOverride::office)
        .findFirst()
        .orElse(DEFAULT_OFFICE);
  }

  @SuppressWarnings("unused")
  private static String normaliseAreaOfLaw(String areaOfLawLiteral) {
    String expected = areaOfLawLiteral.toUpperCase(Locale.ROOT).trim();
    return ("LEGALHELP".equals(expected)) ? "LEGAL HELP" : expected;
  }
}
