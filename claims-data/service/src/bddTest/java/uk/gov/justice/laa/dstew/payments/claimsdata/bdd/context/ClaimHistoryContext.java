package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shared context for claim history BDD scenarios. */
@Component
public class ClaimHistoryContext {

  private UUID currentClaimId;
  private JsonNode lastResponse;

  public UUID getCurrentClaimId() {
    return currentClaimId;
  }

  public void setCurrentClaimId(UUID claimId) {
    this.currentClaimId = claimId;
  }

  public JsonNode getLastResponse() {
    return lastResponse;
  }

  public void setLastResponse(JsonNode response) {
    this.lastResponse = response;
  }

  public void reset() {
    this.currentClaimId = null;
    this.lastResponse = null;
  }
}
