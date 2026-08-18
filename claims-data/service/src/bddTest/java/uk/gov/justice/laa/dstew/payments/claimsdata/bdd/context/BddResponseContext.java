package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import lombok.Getter;
import lombok.Setter;

/** Shared response state for BDD contexts. */
@Getter
@Setter
public abstract class BddResponseContext {

  private Integer lastStatusCode;
  private String lastResponseBody;
}
