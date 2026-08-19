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
    return claimHistoryContext.getLastResponseBody();
  }

  protected void setLastResponse(JsonNode response) {
    claimHistoryContext.setLastResponseBody(response);
  }

  protected UUID requireLastAmendmentId() {
    UUID id = claimHistoryContext.getLastAmendmentId();
    if (id == null) {
      throw new AssertionError(
          "No amendment id has been recorded yet — expected a prior Given step.");
    }
    return id;
  }

  protected void setCurrentClaimSummaryFeeId(UUID id) {
    claimHistoryContext.setCurrentClaimSummaryFeeId(id);
  }

  protected UUID requireCurrentClaimSummaryFeeId() {
    UUID id = claimHistoryContext.getCurrentClaimSummaryFeeId();
    if (id == null) {
      throw new AssertionError(
          "No claim_summary_fee id has been recorded yet — expected a prior Given step.");
    }
    return id;
  }

  protected Integer getLastStatusCode() {
    return claimHistoryContext.getLastStatusCode();
  }

  protected void setLastStatusCode(Integer statusCode) {
    claimHistoryContext.setLastStatusCode(statusCode);
  }
}
