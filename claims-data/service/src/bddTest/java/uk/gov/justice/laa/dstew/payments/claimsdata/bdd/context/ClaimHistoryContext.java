package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import io.cucumber.spring.ScenarioScope;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/** Shared context for claim history BDD scenarios. */
@Component
@ScenarioScope
@Getter
@Setter
public class ClaimHistoryContext extends BddResponseContext {

  private UUID currentClaimId;

  public void reset() {
    super.clear();
    currentClaimId = null;
  }
}
