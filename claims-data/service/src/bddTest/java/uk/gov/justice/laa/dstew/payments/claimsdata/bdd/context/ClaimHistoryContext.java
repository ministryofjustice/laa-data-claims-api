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

  /** The current claim_summary_fee id created alongside the current claim. */
  private UUID currentClaimSummaryFeeId;

  /** Last-seeded amendment id (scenario-scoped). */
  private UUID lastAmendmentId;

  @Override
  public void clear() {
    super.clear();
    currentClaimId = null;
    currentClaimSummaryFeeId = null;
    lastAmendmentId = null;
  }
}
