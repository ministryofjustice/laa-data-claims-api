package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.Format;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.GeneratedFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

/**
 * Step definitions for the API port of the UI {@code mimeChecks.feature}. Each scenario generates a
 * fresh Legal Help bulk-submission file via {@link BulkSubmissionFileGenerator}, uploads it through
 * the real {@code POST /api/v1/bulk-submissions} multipart endpoint with an explicit MIME type, and
 * asserts the synchronous outcome enforced by {@code BulkSubmissionFileValidator}:
 *
 * <ul>
 *   <li>Allowed (extension, content-type) pairs return HTTP 201.
 *   <li>Disallowed pairs return HTTP 415 with the error body {@code "The selected file must be a
 *       valid CSV, XML or TXT file"}.
 * </ul>
 */
public class BulkSubmissionMimeCheckSteps {

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private BulkSubmissionFileGenerator generator;
  @Autowired private SubmissionPeriodHelper periodHelper;

  @Given("the bulk submission MIME checks scaffold is ready")
  public void theBulkSubmissionMimeChecksScaffoldIsReady() {
    // No-op anchor for the feature Background.
  }

  @Given("I generate {string} {string} file with {string} outcomes")
  public void iGenerateFileWithOutcomes(String areaOfLaw, String formatLiteral, String outcomes)
      throws IOException {
    requireLegalHelp(areaOfLaw);
    Format format = Format.fromString(formatLiteral);
    int outcomeCount = Math.max(1, Integer.parseInt(outcomes.trim()));

    String period = periodHelper.nextAvailablePeriod(DEFAULT_OFFICE, AreaOfLaw.LEGAL_HELP);
    GeneratedFile generated =
        generator.generate(format, outcomeCount, DEFAULT_OFFICE, period, java.util.List.of());

    context.setGeneratedFilePath(generated.path());
    context.setLastOffice(generated.office());
    context.setLastSubmissionPeriod(generated.submissionPeriod());
  }

  @When("I upload the generated file with mime type {string}")
  public void iUploadTheGeneratedFileWithMimeType(String mimeType) throws IOException {
    Path file = context.getGeneratedFilePath();
    assertThat(file).as("Generated file must exist before upload").isNotNull();
    String office = context.getLastOffice() != null ? context.getLastOffice() : DEFAULT_OFFICE;
    api.postBulkSubmissionFromPath(file, office, ClaimsDataTestUtil.API_USER_ID, mimeType);
  }

  @Then("the user sees an error message {string}")
  public void theUserSeesAnErrorMessage(String expectedMessage) {
    assertThat(context.getLastStatusCode())
        .as("Expected MIME validation to be rejected with HTTP 415")
        .isEqualTo(415);
    assertThat(context.getLastResponseBody())
        .as("Expected the API error response body to contain the validation message")
        .isNotNull()
        .contains(expectedMessage);
  }

  /**
   * Asserts that the previous MIME-checked upload was accepted end-to-end: HTTP 201, a bulk
   * submission id was returned, and the persisted bulk_submission record exposes the expected area
   * of law. Kept here (rather than in the duplicate-checks step class) so that {@code
   * mimeChecks.feature} owns the definitions of the steps it uses.
   */
  @Then("I should see the submission summary for {string}")
  public void iShouldSeeTheSubmissionSummaryFor(String areaOfLaw) throws IOException {
    assertThat(context.getLastStatusCode())
        .as("Expected POST /bulk-submissions to return 201 but got %s", context.getLastStatusCode())
        .isEqualTo(201);
    UUID id = context.getBulkSubmissionId();
    assertThat(id).as("Bulk submission id must be populated").isNotNull();

    JsonNode bulk = api.getBulkSubmission(id);
    String actual = bulk.path("details").path("schedule").path("area_of_law").asText("");
    String expected =
        "LEGALHELP".equalsIgnoreCase(areaOfLaw.replace(" ", "")) ? "LEGAL HELP" : areaOfLaw;
    assertThat(actual)
        .as("Bulk submission %s should expose details.schedule.area_of_law=%s", id, expected)
        .isEqualToIgnoringCase(expected);
  }

  private static void requireLegalHelp(String areaOfLaw) {
    if (areaOfLaw == null || !areaOfLaw.trim().equalsIgnoreCase("Legal help")) {
      throw new IllegalArgumentException(
          "Only Legal help area of law is supported by this generator (received: "
              + areaOfLaw
              + ")");
    }
  }
}
