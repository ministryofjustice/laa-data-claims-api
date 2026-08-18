package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.When;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;

/** Common step definition for both claim history timeline feature files. */
public class ClaimHistoryTimelineCommonSteps extends ClaimHistoryTimelineSharedSteps {

  @Autowired private BddApiStepSupport api;

  @When("I request the claim history timeline")
  public void iRequestTheClaimHistoryTimeline() {
    UUID claimId = requireCurrentClaimId();
    step(
        "GET /api/v1/claims/" + claimId + "/history",
        () -> {
          JsonNode response = api.getClaimHistoryJson(claimId);
          setLastResponse(response);
          setLastStatusCode(200);
        });
  }
}
