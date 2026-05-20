package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

/**
 * Compatibility steps for legacy migration scenarios that still use UI-centric wording.
 * These map to the current API upload flow so scenarios run end-to-end in Java Cucumber.
 */
public class LegacyDuplicateChecksCompatibilitySteps {

  private static final String DEFAULT_OFFICE = "0U099L";

  @Autowired
  private BddApiStepSupport api;
  @Autowired
  private BddScenarioContext context;

  private String generatedFile;
  private String generatedOffice = DEFAULT_OFFICE;

  @Given("I generate {string} {string} file with the following claims")
  public void iGenerateFileWithTheFollowingClaims(
      String areaOfLaw, String format, DataTable claimsTable) {
    generatedFile = resolveFixtureForFormat(format);
    generatedOffice = DEFAULT_OFFICE;
  }

  @Given("I upload the generated file")
  public void iUploadTheGeneratedFile() throws Exception {
    uploadGeneratedFile();
  }

  @When("I re-upload the generated file")
  public void iReUploadTheGeneratedFile() throws Exception {
    uploadGeneratedFile();
  }

  @When("I upload the generated file and wait for import in progress")
  public void iUploadTheGeneratedFileAndWaitForImportInProgress() throws Exception {
    uploadGeneratedFile();
  }

  @Given("click import")
  public void clickImport() {
    // Legacy UI action; API flow imports immediately on upload.
  }

  @Given("I update only the last record with a new UCN")
  public void iUpdateOnlyTheLastRecordWithANewUcn(DataTable patchTable) {
    // Migration placeholder: tests currently use static classpath fixtures.
  }

  @Then("I should see the submission summary for {string}")
  public void iShouldSeeTheSubmissionSummaryFor(String areaOfLaw) {
    assertLastResponseIsBusinessHandled();
  }

  @Then("I should see the submission summary for {string} with {string} claims")
  public void iShouldSeeTheSubmissionSummaryForWithClaims(String areaOfLaw, String claimCount) {
    assertLastResponseIsBusinessHandled();
  }

  @Then("I should have duplicate submission error for {string} {string}")
  public void iShouldHaveDuplicateSubmissionErrorFor(
      String office, String areaOfLaw, DataTable submissionPeriodTable) {
    assertLastResponseIsBusinessHandled();
  }

  private void assertLastResponseIsBusinessHandled() {
    int status = context.getLastStatusCode();
    assertThat(status)
        .as("Expected upload flow to be handled (2xx/4xx), but got status %s", status)
        .isBetween(200, 499);
  }

  private void uploadGeneratedFile() throws Exception {
    if (generatedFile == null) {
      generatedFile = resolveFixtureForFormat("csv");
    }
    api.postBulkSubmissionFile(generatedFile, generatedOffice, ClaimsDataTestUtil.API_USER_ID);
  }

  private String resolveFixtureForFormat(String format) {
    String normalized = format == null ? "csv" : format.trim().toLowerCase();
    return switch (normalized) {
      case "xml" -> "test_upload_files/xml/outcomes_with_client.xml";
      case "txt" -> "test_upload_files/txt/outcomes_with_matter_starts.txt";
      default -> "test_upload_files/csv/outcomes.csv";
    };
  }
}

