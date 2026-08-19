package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

/** Shared JSON response state for BDD contexts. */
@Getter
@Setter
public abstract class BddResponseContext {

  private Integer lastStatusCode;
  private JsonNode lastResponseBody;

  public void clear() {
    lastStatusCode = null;
    lastResponseBody = null;
  }
}
