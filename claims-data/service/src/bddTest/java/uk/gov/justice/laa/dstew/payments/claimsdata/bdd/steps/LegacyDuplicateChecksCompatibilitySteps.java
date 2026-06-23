package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.ClaimOverride;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.Format;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.LegalHelpFileGenerator.GeneratedFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BulkSubmissionLifecycleSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

/**
 * Step definitions for the API-only port of the UI {@code duplicateChecksLegalHelp.feature}. Every
 * step drives the real laa-data-claims-api HTTP surface:
 *
 * <ul>
 *   <li>{@code I generate ...} - programmatically build a CSV/TXT/XML body via {@link
 *       LegalHelpFileGenerator} and stash the resulting {@link Path} in {@link BddScenarioContext}.
 *   <li>{@code I upload ... [and wait for import in progress]} - POST the generated file to {@code
 *       POST /api/v1/bulk-submissions} (and optionally poll {@code GET
 *       /api/v1/bulk-submissions/{id}/summary} until a terminal status is reached or the polling
 *       budget expires).
 *   <li>{@code click import} - no-op carried over from the UI feature (the API ingests on POST).
 *   <li>{@code I make the generated file invalid} - PATCH the previously-uploaded bulk submission
 *       to {@code VALIDATION_FAILED} via {@code PATCH /api/v1/bulk-submissions/{id}}.
 *   <li>{@code I should see the submission summary for ...} - assert HTTP 201 and verify the
 *       persisted bulk_submission record exposes the expected area_of_law (and optionally the
 *       expected outcome count).
 * </ul>
 */
public class LegacyDuplicateChecksCompatibilitySteps {

  /** Office that has a Legal Help schedule in the local test data setup. */
  private static final String DEFAULT_OFFICE = "0U099L";

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private LegalHelpFileGenerator generator;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private BulkSubmissionLifecycleSupport lifecycleSupport;

  @Given("I generate {string} {string} file with the following claims")
  public void iGenerateFileWithTheFollowingClaims(
      String areaOfLaw, String formatLiteral, DataTable claimsTable) throws IOException {

    requireLegalHelp(areaOfLaw);

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

  @Given("I upload the generated file")
  public void iUploadTheGeneratedFile() throws IOException {
    uploadGeneratedFile();
  }

  @When("I upload the generated file and wait for import in progress")
  public void iUploadTheGeneratedFileAndWaitForImportInProgress() throws IOException {
    uploadGeneratedFile();
    UUID bulkId = context.getBulkSubmissionId();
    assertThat(bulkId)
        .as("Bulk submission id must be present before polling for terminal status")
        .isNotNull();
    String terminal = api.waitForBulkSubmissionTerminalStatus(bulkId);
    context.setLastResponseBody(
        (context.getLastResponseBody() == null ? "" : context.getLastResponseBody())
            + "\n(bulk-status="
            + terminal
            + ")");
  }

  @Given("click import")
  public void clickImport() {
    // Legacy UI action - the API flow ingests on POST, so nothing to do here.
  }

  @Given("I make the generated file invalid")
  public void iMakeTheGeneratedFileInvalid() {
    UUID bulkId = context.getBulkSubmissionId();
    assertThat(bulkId)
        .as("A bulk submission must already exist before it can be marked invalid")
        .isNotNull();
    lifecycleSupport.markBulkSubmissionAsInvalid(bulkId);
  }

  @Then("I should see the submission summary for {string}")
  public void iShouldSeeTheSubmissionSummaryFor(String areaOfLaw) throws IOException {
    assertLastUploadAccepted();
    assertSubmissionAreaOfLawIs(areaOfLaw);
  }

  @Then("I should see the submission summary for {string} with {string} claims")
  public void iShouldSeeTheSubmissionSummaryForWithClaims(String areaOfLaw, String claimCount)
      throws IOException {
    assertLastUploadAccepted();
    assertSubmissionAreaOfLawIs(areaOfLaw);
    assertBulkSubmissionContainsExpectedOutcomeCount(Integer.parseInt(claimCount.trim()));
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private void uploadGeneratedFile() throws IOException {
    Path file = context.getGeneratedFilePath();
    assertThat(file).as("Generated file must exist before upload").isNotNull();
    String office = context.getLastOffice() != null ? context.getLastOffice() : DEFAULT_OFFICE;
    api.postBulkSubmissionFromPath(file, office, ClaimsDataTestUtil.API_USER_ID);
  }

  private void assertLastUploadAccepted() {
    assertThat(context.getLastStatusCode())
        .as(
            "Expected bulk-submission upload to succeed (HTTP 201) but got %s",
            context.getLastStatusCode())
        .isEqualTo(201);
    assertThat(context.getBulkSubmissionId())
        .as("Bulk submission id should be populated after a successful upload")
        .isNotNull();
  }

  private void assertSubmissionAreaOfLawIs(String areaOfLawLiteral) throws IOException {
    String expected = areaOfLawLiteral.toUpperCase(Locale.ROOT).trim();
    if ("LEGAL HELP".equals(expected) || "LEGALHELP".equals(expected)) {
      expected = "LEGAL HELP";
    }

    UUID bulkId = context.getBulkSubmissionId();
    assertThat(bulkId).as("Bulk submission id must be present").isNotNull();

    JsonNode bulk = api.getBulkSubmission(bulkId);
    String areaOfLaw = bulk.path("details").path("schedule").path("area_of_law").asText("");
    assertThat(areaOfLaw)
        .as("Bulk submission %s should expose details.schedule.area_of_law=%s", bulkId, expected)
        .isEqualToIgnoringCase(expected);
  }

  private void assertBulkSubmissionContainsExpectedOutcomeCount(int expectedClaimCount)
      throws IOException {
    UUID bulkId = context.getBulkSubmissionId();
    assertThat(bulkId).as("Bulk submission id must be present").isNotNull();

    JsonNode bulk = api.getBulkSubmission(bulkId);
    JsonNode outcomes = bulk.path("details").path("outcomes");
    int actualOutcomes = outcomes.isArray() ? outcomes.size() : 0;

    assertThat(actualOutcomes)
        .as("Bulk submission %s should contain %d outcome(s)", bulkId, expectedClaimCount)
        .isEqualTo(expectedClaimCount);
  }

  private int countOutcomeLinesInLastGeneratedFile() {
    Path file = context.getGeneratedFilePath();
    if (file == null) {
      return 0;
    }
    try {
      String content = Files.readString(file, StandardCharsets.UTF_8);
      if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")) {
        return countMatches(content, "<outcome>") + countMatches(content, "<outcome ");
      }
      int count = 0;
      for (String line : content.split("\n")) {
        if (line.startsWith("OUTCOME")) {
          count++;
        }
      }
      return count;
    } catch (IOException e) {
      return 0;
    }
  }

  private static int countMatches(String haystack, String needle) {
    int idx = 0;
    int count = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static void requireLegalHelp(String areaOfLaw) {
    if (areaOfLaw == null || !areaOfLaw.trim().equalsIgnoreCase("Legal help")) {
      throw new IllegalArgumentException(
          "Only Legal help area of law is supported by this generator (received: "
              + areaOfLaw
              + ")");
    }
  }

  private static String pickOffice(List<ClaimOverride> overrides) {
    for (ClaimOverride o : overrides) {
      if (o != null && o.office() != null) {
        return o.office();
      }
    }
    return DEFAULT_OFFICE;
  }
}
