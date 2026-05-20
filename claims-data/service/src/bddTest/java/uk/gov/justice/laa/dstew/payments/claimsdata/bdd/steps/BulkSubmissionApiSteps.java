package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

public class BulkSubmissionApiSteps {

  @Autowired private BddApiStepSupport api;
  private String lastFile;
  private String lastOffice;

  @Given("I submit Legal Help bulk file {string} for office {string}")
  public void iSubmitLegalHelpBulkFileForOffice(String classpathFile, String office) throws Exception {
    lastFile = classpathFile;
    lastOffice = office;
    api.postBulkSubmissionFile(classpathFile, office, ClaimsDataTestUtil.API_USER_ID);
  }

  @Given("I re-submit the same Legal Help bulk file")
  public void iResubmitTheSameLegalHelpBulkFile() throws Exception {
    if (lastFile == null || lastOffice == null) {
      throw new IllegalStateException("No previous Legal Help upload is available to re-submit.");
    }
    api.postBulkSubmissionFile(lastFile, lastOffice, ClaimsDataTestUtil.API_USER_ID);
  }

  @Then("the API response status should be {int}")
  public void theApiResponseStatusShouldBe(int statusCode) {
    api.assertLastResponseStatus(statusCode);
  }

  @Then("a bulk submission id is returned")
  public void aBulkSubmissionIdIsReturned() {
    api.assertBulkSubmissionIdPresent();
  }

  @Then("the response contains {int} submission ids")
  public void theResponseContainsSubmissionIds(int expectedCount) {
    api.assertSubmissionCount(expectedCount);
  }

  @Then("the scenario has {int} accepted bulk uploads")
  public void theScenarioHasAcceptedBulkUploads(int expectedCount) {
    api.assertBulkSubmissionRequestCount(expectedCount);
  }

  @Then("all bulk submission ids are unique")
  public void allBulkSubmissionIdsAreUnique() {
    api.assertAllBulkSubmissionIdsAreUnique();
  }
}

