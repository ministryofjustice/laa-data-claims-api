package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.When;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.ClaimHistoryContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;

/** Common step definition for both claim history timeline feature files. */
public class ClaimHistoryTimelineCommonSteps {

  @Autowired private BddApiStepSupport api;
  @Autowired private ClaimHistoryContext claimHistoryContext;

  @When("I request the claim history timeline")
  public void iRequestTheClaimHistoryTimeline() {
    step(
        "GET /api/v1/claims/" + claimHistoryContext.getCurrentClaimId() + "/history",
        () -> {
          UUID claimId = claimHistoryContext.getCurrentClaimId();
          if (claimId == null) {
            throw new AssertionError(
                "No claim id has been established yet — expected a prior Given step.");
          }
          JsonNode response = api.getClaimHistoryJson(claimId);
          claimHistoryContext.setLastResponse(response);
        });
  }
}
