package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

public class PreSubmissionApprovalSteps {

  private static final String APPROVING_USER_ID = "bdd-approving-user";
  private static final String BDD_USER_ID = "bdd-user";

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private ClaimsApiProperties claimsApiProperties;
  @Autowired private SubmissionRepository submissionRepository;

  private UUID submissionId;
  private String office;

  @Given("the pre-submission feature flag is enabled")
  public void thePreSubmissionFeatureFlagIsEnabled() {
    claimsApiProperties.getFeatures().setValidatedPendingApproval("true");
  }

  @Given("a submission is awaiting provider approval")
  public void aSubmissionIsAwaitingProviderApproval() {
    office = DEFAULT_OFFICE;
    submissionId = Uuid7.timeBasedUuid();
    Submission submission =
        Submission.builder()
            .id(submissionId)
            .officeAccountNumber(office)
            .submissionPeriod(periodHelper.nextAvailablePeriod(office, AreaOfLaw.LEGAL_HELP))
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.VALIDATED_PENDING_APPROVAL)
            .isNilSubmission(true)
            .numberOfClaims(0)
            .createdByUserId(BDD_USER_ID)
            .providerUserId(BDD_USER_ID)
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(submission);
  }

  @When("the provider approves the submission")
  public void theProviderApprovesTheSubmission() {
    api.patchSubmission(
        submissionId,
        """
        {
          "status": "READY_FOR_FINAL_VALIDATION",
          "office_account_number": "%s",
          "created_by_user_id": "%s"
        }
        """
            .formatted(office, APPROVING_USER_ID));
    assertThat(context.getLastStatusCode()).as("Approval PATCH response").isEqualTo(204);
  }

  @Then("the submission approval fields are recorded")
  public void theSubmissionApprovalFieldsAreRecorded() throws IOException {
    assertThat(submissionId).as("Approved submission id").isNotNull();
    JsonNode submission = api.getSubmission(submissionId);

    assertThat(submission.path("status").asText())
        .as("status after approval")
        .isEqualTo(SubmissionStatus.READY_FOR_FINAL_VALIDATION.name());
    assertThat(submission.path("approved_on").asText())
        .as("approved_on in submission detail response")
        .isNotBlank();
    assertThat(submission.path("approved_by_user_id").asText())
        .as("approved_by_user_id in submission detail response")
        .isEqualTo(APPROVING_USER_ID);
  }
}
