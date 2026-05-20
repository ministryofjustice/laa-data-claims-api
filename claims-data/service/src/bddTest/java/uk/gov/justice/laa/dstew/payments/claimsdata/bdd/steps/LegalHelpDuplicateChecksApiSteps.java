package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

public class LegalHelpDuplicateChecksApiSteps {

  @Autowired private BddScenarioContext context;
  @Autowired private SubmissionRepository submissionRepository;

  @Given("a prior Legal Help submission exists for office {string} in submission period {string}")
  public void aPriorLegalHelpSubmissionExistsForOfficeInSubmissionPeriod(
      String office, String submissionPeriod) {
    Submission priorSubmission =
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .bulkSubmissionId(Uuid7.timeBasedUuid())
            .officeAccountNumber(office)
            .submissionPeriod(submissionPeriod)
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .createdByUserId("prior-test-user")
            .numberOfClaims(1)
            .build();

    submissionRepository.save(priorSubmission);
    context.getSubmissionIds().add(priorSubmission.getId());
  }

  @Then("I verify {int} total submissions exist in the scenario")
  public void iVerifyTotalSubmissionsExistInTheScenario(int expectedCount) {
    org.assertj.core.api.Assertions.assertThat(context.getSubmissionIds())
        .as("Expected %d submission IDs in scenario context", expectedCount)
        .hasSize(expectedCount);
  }
}

