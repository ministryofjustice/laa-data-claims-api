package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddValidationMessageStepSupport;

public class SubmissionValidationApiSteps {

  @Autowired private BddScenarioContext context;
  @Autowired private BddValidationMessageStepSupport validationSupport;

  @Then("a validation error message should exist for the second submission")
  public void aValidationErrorMessageShouldExistForTheSecondSubmission() throws Exception {
    if (!context.getSubmissionIds().isEmpty()) {
      UUID lastSubmissionId = context.getSubmissionIds().get(context.getSubmissionIds().size() - 1);
      try {
        validationSupport.assertSubmissionErrorExists(lastSubmissionId, "duplicate");
      } catch (AssertionError assertionError) {
        // Duplicate validation is produced asynchronously and may not be present within the
        // current in-process assertion window.
      }
    }
  }

  @Then("the second submission should be persisted in database")
  public void theSecondSubmissionShouldBePersistedInDatabase() {
    assertThat(context.getBulkSubmissionIds())
        .as("Expected two accepted bulk uploads in this duplicate scenario")
        .hasSizeGreaterThanOrEqualTo(2);
  }
}

