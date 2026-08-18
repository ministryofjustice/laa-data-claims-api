package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.ClaimHistoryContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;

/**
 * Shared utilities and context management for claim history BDD scenarios. Does NOT define steps
 * itself (Cucumber forbids extending classes with step definitions), but provides helper methods
 * for both ContractSteps and ParentSteps.
 */
public class ClaimHistoryTimelineSharedSteps {

  @Autowired protected BddApiStepSupport api;
  @Autowired protected ClaimHistoryContext claimHistoryContext;

  protected UUID currentClaimId;
  protected JsonNode lastResponse;

  protected UUID requireCurrentClaimId() {
    UUID id = claimHistoryContext.getCurrentClaimId();
    if (id == null) {
      throw new AssertionError(
          "No claim id has been established yet — expected a prior Given step.");
    }
    return id;
  }

  protected void setCurrentClaimId(UUID id) {
    this.currentClaimId = id;
    claimHistoryContext.setCurrentClaimId(id);
  }

  protected JsonNode getLastResponse() {
    return claimHistoryContext.getLastResponse() != null
        ? claimHistoryContext.getLastResponse()
        : lastResponse;
  }

  protected void setLastResponse(JsonNode response) {
    this.lastResponse = response;
    claimHistoryContext.setLastResponse(response);
  }
}
